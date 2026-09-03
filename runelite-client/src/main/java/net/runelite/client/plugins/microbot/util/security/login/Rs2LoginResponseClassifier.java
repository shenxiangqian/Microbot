package net.runelite.client.plugins.microbot.util.security.login;

import lombok.Value;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Classifies normalized login response text and RuneLite client state. */
public final class Rs2LoginResponseClassifier
{
	private static final List<Rule> RESPONSE_RULES = Arrays.asList(
		all(Rs2LoginStatus.TOO_MANY_ATTEMPTS_LAUNCHER, "too many login attempts", "launcher"),
		any(Rs2LoginStatus.BAD_AUTH_CODE,
			"incorrect authenticator code", "code you entered was incorrect", "incorrect code"),
		any(Rs2LoginStatus.SERVER_UPDATING,
			"game servers are currently being updated", "servers are currently being updated"),
		any(Rs2LoginStatus.UNEXPECTED_LOGIN_RESPONSE,
			"unexpected loginserver response", "unexpected login server response"),
		any(Rs2LoginStatus.UNEXPECTED_SERVER_RESPONSE, "unexpected server response"),
		any(Rs2LoginStatus.MEMBERS_AREA,
			"standing in a members-only area", "move your character to a non-members area",
			"move your character to a non members area"),
		any(Rs2LoginStatus.MEMBERS_WORLD,
			"need a members' account to use this world", "need a members account to use this world"),
		any(Rs2LoginStatus.MEMBERS_WORLD_2, "subscribe to login to this world"),
		any(Rs2LoginStatus.ENTER_USERNAME,
			"please enter your username/email address", "please enter your username or email address",
			"please enter your username"),
		any(Rs2LoginStatus.CONNECTION_TIMED_OUT, "connection timed out", "login attempt timed out"),
		any(Rs2LoginStatus.ERROR_CONNECTING, "error connecting to server"),
		any(Rs2LoginStatus.FAILED_TO_LOGIN, "failed to login"),
		any(Rs2LoginStatus.NO_RESPONSE, "no response from server"),
		any(Rs2LoginStatus.NOT_LOGGED_OUT, "not logged out", "have not logged out"),
		any(Rs2LoginStatus.STILL_LOGGED_IN,
			"account is still logged in", "either your account is still logged in"),
		any(Rs2LoginStatus.INVALID_LOGIN,
			"incorrect username or password", "invalid username or password", "invalid credentials"),
		any(Rs2LoginStatus.DISABLED,
			"account has been disabled", "account is disabled", "serious rule breaking"),
		any(Rs2LoginStatus.ALREADY_LOGGED_IN, "already logged in"),
		any(Rs2LoginStatus.UPDATED,
			"runescape has been updated", "old school runescape has been updated"),
		any(Rs2LoginStatus.FULL_WORLD, "world is full"),
		any(Rs2LoginStatus.UNABLE_TO_CONNECT, "unable to connect"),
		any(Rs2LoginStatus.LOGIN_LIMIT_EXCEEDED, "login limit exceeded"),
		any(Rs2LoginStatus.BAD_SESSION,
			"bad session id", "invalid session id", "session has expired"),
		any(Rs2LoginStatus.PASSWORD_KNOWN,
			"someone knows your password", "someone may know your password"),
		any(Rs2LoginStatus.FAILED_TO_COMPLETE_LOGIN,
			"could not complete login", "failed to complete login"),
		any(Rs2LoginStatus.SERVER_UPDATED, "the server is being updated"),
		any(Rs2LoginStatus.TOO_MANY_ATTEMPTS, "too many login attempts"),
		any(Rs2LoginStatus.ACCOUNT_LOCKED, "account has been locked", "account is locked"),
		any(Rs2LoginStatus.CLOSED_BETA, "closed beta"),
		any(Rs2LoginStatus.INVALID_LOGIN_SERVER,
			"invalid loginserver requested", "invalid login server requested"),
		any(Rs2LoginStatus.MALFORMED_PACKET, "malformed login packet", "malformed packet"),
		any(Rs2LoginStatus.NO_REPLY, "no reply from loginserver", "no reply from login server"),
		any(Rs2LoginStatus.ERROR_LOADING_PROFILE, "error loading your profile", "error loading profile"),
		any(Rs2LoginStatus.ADDRESS_BLOCKED,
			"computer's address has been blocked", "computers address has been blocked",
			"address has been blocked"),
		any(Rs2LoginStatus.SERVICE_UNAVAILABLE, "service unavailable"),
		any(Rs2LoginStatus.SET_DISPLAY_NAME,
			"displayname set", "set a display name", "display name must be set"),
		any(Rs2LoginStatus.UNSUCCESSFUL_LOGIN,
			"attempt to log into your account was unsuccessful", "login was unsuccessful",
			"unpaid balance on your account"),
		any(Rs2LoginStatus.INACCESSIBLE, "account is currently inaccessible", "account is inaccessible"),
		any(Rs2LoginStatus.VOTE, "must vote to play", "vote to play"),
		any(Rs2LoginStatus.NOT_ELIGIBLE, "account is not eligible to play", "not eligible to play"),
		any(Rs2LoginStatus.ENTER_AUTH,
			"enter your authenticator code", "enter the 6-digit code", "authenticator app"),
		all(Rs2LoginStatus.TOTAL_LEVEL, "you need a total of", "skills to play on this world"),
		any(Rs2LoginStatus.TOTAL_LEVEL, "total level of", "total skill level"),
		any(Rs2LoginStatus.WORLD_LOCKED,
			"world is currently restricted", "world is locked", "world is currently closed"),
		any(Rs2LoginStatus.SIGNED_OUT, "signed out", "you were disconnected from the server"),
		any(Rs2LoginStatus.CONNECTING_TO_SERVER, "connecting to server"));

	public Classification classify(GameState gameState, int loginIndex, List<String> responseLines)
	{
		GameState safeGameState = gameState == null ? GameState.UNKNOWN : gameState;
		if (safeGameState == GameState.LOGGED_IN)
		{
			return new Classification(Rs2LoginStatus.LOGGED_IN, Rs2LoginStatusSource.GAME_STATE);
		}
		if (safeGameState == GameState.LOGIN_SCREEN && loginIndex == 0)
		{
			return new Classification(Rs2LoginStatus.LOGIN_SCREEN, Rs2LoginStatusSource.LOGIN_INDEX);
		}

		if (isLoginScreen(safeGameState))
		{
			String response = normalize(responseLines);
			if (!response.isEmpty())
			{
				for (Rule rule : RESPONSE_RULES)
				{
					if (rule.matches(response))
					{
						return new Classification(rule.status, Rs2LoginStatusSource.RESPONSE_TEXT);
					}
				}
			}
		}

		if (safeGameState == GameState.LOGIN_SCREEN_AUTHENTICATOR)
		{
			return new Classification(Rs2LoginStatus.ENTER_AUTH, Rs2LoginStatusSource.GAME_STATE);
		}
		if (safeGameState == GameState.LOGGING_IN
			|| safeGameState == GameState.LOADING
			|| safeGameState == GameState.HOPPING)
		{
			return new Classification(Rs2LoginStatus.CONNECTING_TO_SERVER, Rs2LoginStatusSource.GAME_STATE);
		}
		if (safeGameState == GameState.CONNECTION_LOST)
		{
			return new Classification(Rs2LoginStatus.SIGNED_OUT, Rs2LoginStatusSource.GAME_STATE);
		}
		if (safeGameState == GameState.LOGIN_SCREEN && loginIndex == 4)
		{
			return new Classification(Rs2LoginStatus.ENTER_AUTH, Rs2LoginStatusSource.LOGIN_INDEX);
		}
		if (safeGameState == GameState.LOGIN_SCREEN)
		{
			return new Classification(Rs2LoginStatus.LOGIN_SCREEN, Rs2LoginStatusSource.GAME_STATE);
		}
		return new Classification(Rs2LoginStatus.UNKNOWN, Rs2LoginStatusSource.FALLBACK);
	}

	static String normalize(List<String> responseLines)
	{
		if (responseLines == null || responseLines.isEmpty())
		{
			return "";
		}
		return responseLines.stream()
			.filter(Objects::nonNull)
			.map(Rs2TextSanitizer::normalizeGameText)
			.map(Rs2TextSanitizer::decodeKnownEntities)
			.map(Rs2TextSanitizer::stripTagsToSpace)
			.map(value -> value.toLowerCase(Locale.ROOT))
			.collect(Collectors.joining(" "))
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static boolean isLoginScreen(GameState gameState)
	{
		return gameState == GameState.LOGIN_SCREEN || gameState == GameState.LOGIN_SCREEN_AUTHENTICATOR;
	}

	private static Rule any(Rs2LoginStatus status, String... phrases)
	{
		List<List<String>> alternatives = new ArrayList<>();
		for (String phrase : phrases)
		{
			alternatives.add(Collections.singletonList(phrase));
		}
		return new Rule(status, alternatives);
	}

	private static Rule all(Rs2LoginStatus status, String... fragments)
	{
		return new Rule(status, Collections.singletonList(Arrays.asList(fragments)));
	}

	@Value
	public static class Classification
	{
		Rs2LoginStatus status;
		Rs2LoginStatusSource source;
	}

	private static final class Rule
	{
		private final Rs2LoginStatus status;
		private final List<List<String>> alternatives;

		private Rule(Rs2LoginStatus status, List<List<String>> alternatives)
		{
			this.status = status;
			this.alternatives = alternatives;
		}

		private boolean matches(String response)
		{
			return alternatives.stream()
				.anyMatch(alternative -> alternative.stream().allMatch(response::contains));
		}
	}
}
