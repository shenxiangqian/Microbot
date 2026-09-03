package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.security.login.LoginResponseSnapshot;
import net.runelite.client.plugins.microbot.util.security.login.Rs2LoginResponse;
import net.runelite.client.plugins.microbot.util.security.login.Rs2LoginStatus;
import net.runelite.client.plugins.microbot.util.security.login.Rs2LoginStatusSource;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class LoginHandler extends AgentHandler {

	private static final int DEFAULT_TIMEOUT_SECONDS = 30;
	private static final int MAX_TIMEOUT_SECONDS = 120;
	private static final int LOGIN_STABILIZATION_MS = 3000;
	private static final int REPEATED_RESPONSE_GRACE_MS = 1500;

	private final Client client;
	private final Supplier<LoginResponseSnapshot> loginSnapshotSupplier;

	public LoginHandler(Gson gson, Client client) {
		this(gson, client, () -> Rs2LoginResponse.getSnapshot(client));
	}

	LoginHandler(Gson gson, Client client, Supplier<LoginResponseSnapshot> loginSnapshotSupplier) {
		super(gson);
		this.client = client;
		this.loginSnapshotSupplier = loginSnapshotSupplier;
	}

	@Override
	public String getPath() {
		return "/login";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod().toUpperCase();

		if ("GET".equals(method)) {
			handleStatus(exchange);
		} else if ("POST".equals(method)) {
			handleLogin(exchange);
		} else {
			sendJson(exchange, 405, errorResponse("Use GET or POST"));
		}
	}

	private void handleStatus(HttpExchange exchange) throws IOException {
		sendJson(exchange, 200, buildStatusMap());
	}

	private Map<String, Object> buildStatusMap() {
		Map<String, Object> result = new LinkedHashMap<>();
		LoginResponseSnapshot snapshot = getLoginSnapshot();

		GameState gameState = snapshot.getGameState();
		result.put("loggedIn", snapshot.getStatus() == Rs2LoginStatus.LOGGED_IN);
		result.put("gameState", gameState.name());
		result.put("loginAttemptActive", LoginManager.isLoginAttemptActive());
		putDetailedStatus(result, snapshot);

		if (snapshot.getStatus() == Rs2LoginStatus.LOGGED_IN) {
			long durationMs = LoginManager.getLoginDuration().toMillis();
			result.put("loginDurationMs", durationMs);
		}

		try {
			ConfigProfile profile = LoginManager.getActiveProfile();
			if (profile != null) {
				Map<String, Object> profileInfo = new LinkedHashMap<>();
				profileInfo.put("name", profile.getName());
				profileInfo.put("isMember", profile.isMember());
				profileInfo.put("selectedWorld", profile.getSelectedWorld());
				result.put("activeProfile", profileInfo);
			}
		} catch (Exception e) {
			log.debug("Failed to read active profile", e);
		}

		result.put("currentWorld", client.getWorld());

		return result;
	}

	private void handleLogin(HttpExchange exchange) throws IOException {
		LoginResponseSnapshot initialSnapshot = getLoginSnapshot();
		if (initialSnapshot.getStatus() == Rs2LoginStatus.LOGGED_IN) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("message", "Already logged in");
			result.put("currentWorld", client.getWorld());
			putDetailedStatus(result, initialSnapshot);
			sendJson(exchange, 200, result);
			return;
		}

		if (LoginManager.isLoginAttemptActive()) {
			sendJson(exchange, 409, errorResponse("Login attempt already in progress"));
			return;
		}

		Map<String, Object> body = readJsonBody(exchange);

		int targetWorld = -1;
		boolean wait = true;
		int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

		if (body != null) {
			if (body.containsKey("world")) {
				targetWorld = ((Number) body.get("world")).intValue();
			}
			if (body.containsKey("wait")) {
				wait = Boolean.TRUE.equals(body.get("wait"));
			}
			if (body.containsKey("timeout")) {
				timeoutSeconds = Math.min(((Number) body.get("timeout")).intValue(), MAX_TIMEOUT_SECONDS);
				if (timeoutSeconds <= 0) timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
			}
		}

		boolean loginInitiated;
		try {
			if (targetWorld > 0) {
				loginInitiated = LoginManager.login(targetWorld);
			} else {
				loginInitiated = LoginManager.login();
			}
		} catch (Exception e) {
			log.error("Login attempt failed", e);
			sendJson(exchange, 500, errorResponse("Login failed: " + e.getMessage()));
			return;
		}

		if (!loginInitiated) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", false);
			result.put("message", "Login rejected - check that an active profile is configured and client is on the login screen");
			sendJson(exchange, 400, result);
			return;
		}

		if (!wait) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("message", "Login initiated (not waiting for result)");
			if (targetWorld > 0) {
				result.put("world", targetWorld);
			}
			sendJson(exchange, 200, result);
			return;
		}

		LoginResult loginResult = waitForLoginResult(timeoutSeconds, initialSnapshot);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", loginResult.success);
		result.put("message", loginResult.message);
		result.put("currentWorld", client.getWorld());

		putDetailedStatus(result, loginResult.snapshot);

		sendJson(exchange, loginResult.success ? 200 : 401, result);
	}

	private LoginResult waitForLoginResult(int timeoutSeconds, LoginResponseSnapshot initialSnapshot) {
		long startedAt = System.currentTimeMillis();
		AtomicBoolean sawProgress = new AtomicBoolean(false);
		AtomicReference<Long> loggedInSince = new AtomicReference<>();
		AtomicReference<LoginResult> outcome = new AtomicReference<>();
		AtomicReference<LoginResponseSnapshot> latest = new AtomicReference<>(initialSnapshot);

		sleepUntil(() -> {
			LoginResponseSnapshot snapshot = getLoginSnapshot();
			latest.set(snapshot);
			Rs2LoginStatus status = snapshot.getStatus();

			if (status == Rs2LoginStatus.CONNECTING_TO_SERVER) {
				sawProgress.set(true);
				loggedInSince.set(null);
				return false;
			}
			if (status == Rs2LoginStatus.LOGGED_IN) {
				sawProgress.set(true);
				Long stableSince = loggedInSince.updateAndGet(value ->
					value == null ? System.currentTimeMillis() : value);
				if (System.currentTimeMillis() - stableSince >= LOGIN_STABILIZATION_MS) {
					outcome.set(LoginResult.ok("Login successful", snapshot));
					return true;
				}
				return false;
			}

			loggedInSince.set(null);
			if (status.isTerminal()
				&& (sawProgress.get()
					|| !sameResponse(initialSnapshot, snapshot)
					|| System.currentTimeMillis() - startedAt >= REPEATED_RESPONSE_GRACE_MS)) {
				outcome.set(LoginResult.fail("Login failed: " + describe(snapshot), snapshot));
				return true;
			}
			return false;
		}, timeoutSeconds * 1000);

		LoginResult result = outcome.get();
		if (result != null) {
			return result;
		}
		if (Thread.currentThread().isInterrupted()) {
			return LoginResult.fail("Login interrupted", withStatus(latest.get(), Rs2LoginStatus.FAILED_TO_LOGIN));
		}
		return LoginResult.fail(
			"Login timed out after " + timeoutSeconds + "s",
			withStatus(latest.get(), Rs2LoginStatus.CONNECTION_TIMED_OUT));
	}

	private LoginResponseSnapshot getLoginSnapshot() {
		try {
			return loginSnapshotSupplier.get();
		} catch (RuntimeException e) {
			log.debug("Failed to capture detailed login status", e);
			return Rs2LoginResponse.classifySnapshot(
				GameState.UNKNOWN, -1, java.util.Collections.emptyList(), false, "unavailable");
		}
	}

	private static boolean sameResponse(LoginResponseSnapshot first, LoginResponseSnapshot second) {
		return first.getStatus() == second.getStatus()
			&& first.getResponseText().equals(second.getResponseText());
	}

	private static LoginResponseSnapshot withStatus(
		LoginResponseSnapshot snapshot,
		Rs2LoginStatus status) {
		return new LoginResponseSnapshot(
			status,
			snapshot.getGameState(),
			snapshot.getLoginIndex(),
			snapshot.getResponseLines(),
			Rs2LoginStatusSource.FALLBACK,
			Instant.now(),
			snapshot.isResponseTextAvailable(),
			snapshot.getReflectionMappingVersion());
	}

	private static String describe(LoginResponseSnapshot snapshot) {
		return snapshot.hasResponseText() ? snapshot.getResponseText() : snapshot.getStatus().name();
	}

	private static void putDetailedStatus(Map<String, Object> result, LoginResponseSnapshot snapshot) {
		result.put("loginStatus", snapshot.getStatus().name());
		result.put("loginStatusSeverity", snapshot.getStatus().getSeverity());
		result.put("loginStatusSource", snapshot.getSource().name());
		result.put("loginResponseMappingVersion", snapshot.getReflectionMappingVersion());
		result.put("responseTextAvailable", snapshot.isResponseTextAvailable());
		if (snapshot.getLoginIndex() >= 0) {
			result.put("loginIndex", snapshot.getLoginIndex());
		}
		if (snapshot.hasResponseText()) {
			result.put("loginResponseLines", snapshot.getResponseLines());
		}
		if (snapshot.getStatus().isTerminal()) {
			result.put("loginError", describe(snapshot));
		}
	}

	private static class LoginResult {
		final boolean success;
		final String message;
		final LoginResponseSnapshot snapshot;

		LoginResult(boolean success, String message, LoginResponseSnapshot snapshot) {
			this.success = success;
			this.message = message;
			this.snapshot = snapshot;
		}

		static LoginResult ok(String message, LoginResponseSnapshot snapshot) {
			return new LoginResult(true, message, snapshot);
		}

		static LoginResult fail(String message, LoginResponseSnapshot snapshot) {
			return new LoginResult(false, message, snapshot);
		}
	}
}
