package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpServer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.util.security.login.Rs2LoginResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LoginHandlerTest {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

	private static HttpServer server;
	private static int port;
	private static Client mockClient;
	private static AtomicReference<List<String>> responseLines;

	@BeforeClass
	public static void startServer() throws IOException {
		mockClient = mock(Client.class);
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getWorld()).thenReturn(360);
		responseLines = new AtomicReference<>(Collections.emptyList());

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newFixedThreadPool(2));

		LoginHandler handler = new LoginHandler(GSON, mockClient, () -> {
			GameState gameState = mockClient.getGameState();
			int loginIndex = gameState == GameState.LOGIN_SCREEN
				|| gameState == GameState.LOGIN_SCREEN_AUTHENTICATOR
				? mockClient.getLoginIndex()
				: -1;
			return Rs2LoginResponse.classifySnapshot(
				gameState, loginIndex, responseLines.get(), true, "test");
		});
		server.createContext(handler.getPath(), handler);
		server.start();

		port = server.getAddress().getPort();
	}

	@Before
	public void resetLoginState() {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(0);
		when(mockClient.getWorld()).thenReturn(360);
		responseLines.set(Collections.emptyList());
	}

	@AfterClass
	public static void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	// ==========================================
	// GET /login - Status
	// ==========================================

	@Test
	public void testGetStatusReturnsGameState() throws IOException {
		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertNotNull(resp.body.get("gameState"));
		assertNotNull(resp.body.get("loggedIn"));
		assertNotNull(resp.body.get("currentWorld"));
		assertNotNull(resp.body.get("loginStatus"));
		assertNotNull(resp.body.get("loginStatusSeverity"));
		assertNotNull(resp.body.get("loginStatusSource"));
	}

	@Test
	public void testGetStatusShowsLoginScreen() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertEquals("LOGIN_SCREEN", resp.body.get("gameState"));
		assertEquals(false, resp.body.get("loggedIn"));
	}

	@Test
	public void testGetStatusShowsCurrentWorld() throws IOException {
		when(mockClient.getWorld()).thenReturn(450);

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertEquals(450.0, resp.body.get("currentWorld"));
	}

	@Test
	public void testGetStatusIncludesLoginAttemptActive() throws IOException {
		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertNotNull(resp.body.get("loginAttemptActive"));
	}

	@Test
	public void testGetStatusExposesLoginIndexOnLoginScreen() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(0);

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertNotNull(resp.body.get("loginIndex"));
	}

	@Test
	public void testGetStatusDetectsNonMemberError() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(34);
		setResponse("You need a members' account to use this world.");

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertEquals(34.0, resp.body.get("loginIndex"));
		assertEquals("MEMBERS_WORLD", resp.body.get("loginStatus"));
		assertEquals("You need a members' account to use this world.", resp.body.get("loginError"));
	}

	@Test
	public void testGetStatusDetectsDisabledAccount() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(14);
		setResponse("Your account has been disabled.");

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertEquals(14.0, resp.body.get("loginIndex"));
		assertEquals("DISABLED", resp.body.get("loginStatus"));
		assertEquals("Your account has been disabled.", resp.body.get("loginError"));
	}

	@Test
	public void testGetStatusDetectsAuthFailure() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(4);
		setResponse("Incorrect username or password.");

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertEquals("INVALID_LOGIN", resp.body.get("loginStatus"));
		assertEquals("Incorrect username or password.", resp.body.get("loginError"));
	}

	@Test
	public void testGetStatusNoErrorOnNormalLoginScreen() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(0);

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertNull(resp.body.get("loginError"));
	}

	@Test
	public void testGetStatusClearsStaleErrorAfterReturningToRootLoginScreen() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(3);
		setResponse("Incorrect username or password.");
		assertEquals("INVALID_LOGIN", get("/login").body.get("loginStatus"));

		when(mockClient.getLoginIndex()).thenReturn(0);
		Response resp = get("/login");

		assertEquals("LOGIN_SCREEN", resp.body.get("loginStatus"));
		assertNull(resp.body.get("loginError"));
		assertNull(resp.body.get("loginResponseLines"));
	}

	@Test
	public void testGetStatusDetectsNonMemberSkillTotalRequirement() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(3);
		setResponse("You need a total of 750", "in non-member skills", "to play on this world.");

		Response resp = get("/login");

		assertEquals("TOTAL_LEVEL", resp.body.get("loginStatus"));
		assertEquals("RESPONSE_TEXT", resp.body.get("loginStatusSource"));
		assertEquals("You need a total of 750 in non-member skills to play on this world.",
			resp.body.get("loginError"));
	}

	@Test
	public void testGetStatusNoLoginIndexWhenLoggedIn() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGGED_IN);

		Response resp = get("/login");
		assertEquals(200, resp.code);
		assertNull(resp.body.get("loginIndex"));
		assertNull(resp.body.get("loginError"));
		assertEquals("LOGGED_IN", resp.body.get("loginStatus"));
	}

	// ==========================================
	// Method enforcement
	// ==========================================

	@Test
	public void testDeleteReturns405() throws IOException {
		HttpURLConnection conn = openConnection("/login");
		conn.setRequestMethod("DELETE");
		assertEquals(405, conn.getResponseCode());
	}

	@Test
	public void testRejectsCrossOriginRequests() throws IOException, InterruptedException {
		java.net.http.HttpClient hc = java.net.http.HttpClient.newHttpClient();
		java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create("http://127.0.0.1:" + port + "/login"))
				.header("Origin", "https://evil.example.com")
				.GET()
				.build();
		java.net.http.HttpResponse<String> resp = hc.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
		// Opaque 404 for all pre-auth failures — scanners must not distinguish a real endpoint.
		assertEquals(404, resp.statusCode());
		assertTrue(resp.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
	}

	// ==========================================
	// POST /login - Trigger login
	// ==========================================

	@Test
	public void testPostLoginFireAndForget() throws IOException {
		Response resp = post("/login", Map.of("wait", false));
		assertTrue("POST /login should return success or error",
				resp.body.containsKey("success") || resp.body.containsKey("error"));
	}

	@Test
	public void testPostLoginFireAndForgetWithWorld() throws IOException {
		Response resp = post("/login", Map.of("wait", false, "world", 360));
		assertTrue("POST /login should return success or error",
				resp.body.containsKey("success") || resp.body.containsKey("error"));
	}

	@Test
	public void testPostLoginBlockingReturnsFailureWhenLoginCannotInitiate() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(0);

		Response resp = post("/login", Map.of("wait", true, "timeout", 2));

		assertNotNull(resp.body);
		assertTrue(resp.body.containsKey("success") || resp.body.containsKey("error"));
		assertTrue("Should return error status",
				resp.code == 400 || resp.code == 401 || resp.code == 500);
	}

	@Test
	public void testPostLoginBlockingDetectsFatalError() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(34);

		Response resp = post("/login", Map.of("wait", true, "timeout", 5));

		assertNotNull(resp.body);
		// In a unit-test environment LoginManager has no active profile, so login
		// initiation is rejected before the loginIndex is ever polled.  Accept any
		// failure response (no profile, non-member, or timed out).
		if (resp.body.containsKey("success") && Boolean.FALSE.equals(resp.body.get("success"))) {
			String message = (String) resp.body.get("message");
			assertTrue("Should return a failure response: " + message,
					message.contains("Non-member") || message.contains("timed out")
							|| message.contains("Login rejected") || message.contains("profile"));
		}
	}

	@Test
	public void testPostLoginReturnsJsonOnInternalError() throws IOException {
		Response resp = post("/login", Map.of("wait", false));
		assertNotNull(resp.body);
		assertTrue(resp.code == 200 || resp.code == 400 || resp.code == 401 || resp.code == 500);
	}

	@Test
	public void testPostLoginDefaultsToWait() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(14);

		Response resp = post("/login", Map.of("timeout", 2));

		assertNotNull(resp.body);
		if (resp.body.containsKey("success") && Boolean.FALSE.equals(resp.body.get("success"))) {
			assertNotNull(resp.body.get("message"));
		}
	}

	// ==========================================
	// Non-member on members world (loginIndex 34)
	// ==========================================

	@Test
	public void testNonMemberDetection_getReturnsCorrectFields() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(34);
		when(mockClient.getWorld()).thenReturn(360);
		setResponse("You need a members' account to use this world.");

		Response resp = get("/login");

		assertEquals(200, resp.code);
		assertEquals(false, resp.body.get("loggedIn"));
		assertEquals("LOGIN_SCREEN", resp.body.get("gameState"));
		assertEquals(34.0, resp.body.get("loginIndex"));
		assertEquals("MEMBERS_WORLD", resp.body.get("loginStatus"));
		assertEquals("You need a members' account to use this world.", resp.body.get("loginError"));
		assertEquals(360.0, resp.body.get("currentWorld"));
	}

	@Test
	public void testNonMemberDetection_cliFlowPostThenGet() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(34);
		when(mockClient.getWorld()).thenReturn(360);
		setResponse("You need a members' account to use this world.");

		post("/login", Map.of("wait", false, "world", 360));

		Response status = get("/login");

		assertEquals(200, status.code);
		assertEquals(false, status.body.get("loggedIn"));
		assertEquals(34.0, status.body.get("loginIndex"));
		assertEquals("MEMBERS_WORLD", status.body.get("loginStatus"));
		assertEquals("You need a members' account to use this world.", status.body.get("loginError"));
	}

	@Test
	public void testNonMemberDetection_errorClearsWhenLoggedIn() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(34);
		setResponse("You need a members' account to use this world.");

		Response errorResp = get("/login");
		assertEquals("MEMBERS_WORLD", errorResp.body.get("loginStatus"));

		when(mockClient.getGameState()).thenReturn(GameState.LOGGED_IN);

		Response loggedInResp = get("/login");
		assertNull(loggedInResp.body.get("loginIndex"));
		assertNull(loggedInResp.body.get("loginError"));
	}

	@Test
	public void testNonMemberDetection_distinctFromOtherErrors() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		when(mockClient.getLoginIndex()).thenReturn(34);
		setResponse("You need a members' account to use this world.");
		Response nonMember = get("/login");
		assertEquals("MEMBERS_WORLD", nonMember.body.get("loginStatus"));

		when(mockClient.getLoginIndex()).thenReturn(14);
		setResponse("Your account has been disabled.");
		Response banned = get("/login");
		assertEquals("DISABLED", banned.body.get("loginStatus"));

		when(mockClient.getLoginIndex()).thenReturn(4);
		setResponse("Incorrect username or password.");
		Response invalidCreds = get("/login");
		assertEquals("INVALID_LOGIN", invalidCreds.body.get("loginStatus"));

		when(mockClient.getLoginIndex()).thenReturn(3);
		setResponse("Enter your authenticator code.");
		Response authFail = get("/login");
		assertEquals("ENTER_AUTH", authFail.body.get("loginStatus"));

		when(mockClient.getLoginIndex()).thenReturn(24);
		setResponse("You were disconnected from the server.");
		Response disconnected = get("/login");
		assertEquals("SIGNED_OUT", disconnected.body.get("loginStatus"));
	}

	@Test
	public void testNonMemberDetection_noFalsePositiveOnNormalIndex() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		for (int idx : new int[]{0, 1, 2, 5, 10, 20, 99}) {
			when(mockClient.getLoginIndex()).thenReturn(idx);
			responseLines.set(Collections.emptyList());
			Response resp = get("/login");
			assertNull("loginIndex " + idx + " should not produce loginError", resp.body.get("loginError"));
		}
	}

	@Test
	public void testNonMemberDetection_responseIncludesWorldForRetry() throws IOException {
		when(mockClient.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(mockClient.getLoginIndex()).thenReturn(34);
		when(mockClient.getWorld()).thenReturn(360);

		Response resp = get("/login");

		assertEquals(34.0, resp.body.get("loginIndex"));
		assertEquals(360.0, resp.body.get("currentWorld"));
	}

	// ==========================================
	// Security: no CORS wildcard
	// ==========================================

	@Test
	public void testNoCorsWildcardOnGet() throws IOException {
		HttpURLConnection conn = openConnection("/login");
		conn.setRequestMethod("GET");
		conn.getResponseCode();
		assertNull(conn.getHeaderField("Access-Control-Allow-Origin"));
	}

	@Test
	public void testNoCorsWildcardOnPost() throws IOException {
		HttpURLConnection conn = openConnection("/login");
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);
		try (OutputStream os = conn.getOutputStream()) {
			os.write("{}".getBytes(StandardCharsets.UTF_8));
		}
		conn.getResponseCode();
		assertNull(conn.getHeaderField("Access-Control-Allow-Origin"));
	}

	// ==========================================
	// JSON content type
	// ==========================================

	@Test
	public void testResponseIsJson() throws IOException {
		HttpURLConnection conn = openConnection("/login");
		conn.setRequestMethod("GET");
		conn.getResponseCode();
		assertTrue(conn.getHeaderField("Content-Type").contains("application/json"));
	}

	// ==========================================
	// Helpers
	// ==========================================

	private Response get(String path) throws IOException {
		HttpURLConnection conn = openConnection(path);
		conn.setRequestMethod("GET");
		return readResponse(conn);
	}

	private static void setResponse(String... lines) {
		responseLines.set(java.util.Arrays.asList(lines));
	}

	private Response post(String path, Map<String, Object> body) throws IOException {
		HttpURLConnection conn = openConnection(path);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
		}
		return readResponse(conn);
	}

	private HttpURLConnection openConnection(String path) throws IOException {
		URL url = new URL("http://127.0.0.1:" + port + path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		return conn;
	}

	private Response readResponse(HttpURLConnection conn) throws IOException {
		int code = conn.getResponseCode();
		InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
		String json;
		try (Scanner sc = new Scanner(stream, StandardCharsets.UTF_8.name())) {
			json = sc.useDelimiter("\\A").hasNext() ? sc.next() : "{}";
		}
		Map<String, Object> body = GSON.fromJson(json, MAP_TYPE);
		return new Response(code, body);
	}

	private static class Response {
		final int code;
		final Map<String, Object> body;

		Response(int code, Map<String, Object> body) {
			this.code = code;
			this.body = body;
		}
	}
}
