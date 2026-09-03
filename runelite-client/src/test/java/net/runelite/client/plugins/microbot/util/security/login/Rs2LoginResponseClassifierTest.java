package net.runelite.client.plugins.microbot.util.security.login;

import net.runelite.api.GameState;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class Rs2LoginResponseClassifierTest
{
	private final Rs2LoginResponseClassifier classifier = new Rs2LoginResponseClassifier();

	@Test
	public void classifiesKnownResponseMessages()
	{
		Object[][] cases = {
			{Rs2LoginStatus.ENTER_USERNAME, "Please enter your username/email address."},
			{Rs2LoginStatus.CONNECTION_TIMED_OUT, "Connection timed out."},
			{Rs2LoginStatus.ERROR_CONNECTING, "Error connecting to server."},
			{Rs2LoginStatus.FAILED_TO_LOGIN, "Failed to login."},
			{Rs2LoginStatus.NO_RESPONSE, "No response from server."},
			{Rs2LoginStatus.NOT_LOGGED_OUT, "You have not logged out from your last session."},
			{Rs2LoginStatus.STILL_LOGGED_IN, "Either your account is still logged in, or the last world is unavailable."},
			{Rs2LoginStatus.INVALID_LOGIN, "Incorrect username or password."},
			{Rs2LoginStatus.DISABLED, "Your account has been disabled."},
			{Rs2LoginStatus.ALREADY_LOGGED_IN, "This account is already logged in."},
			{Rs2LoginStatus.UPDATED, "RuneScape has been updated!"},
			{Rs2LoginStatus.FULL_WORLD, "This world is full."},
			{Rs2LoginStatus.UNABLE_TO_CONNECT, "Unable to connect."},
			{Rs2LoginStatus.LOGIN_LIMIT_EXCEEDED, "Login limit exceeded."},
			{Rs2LoginStatus.BAD_SESSION, "Invalid session id."},
			{Rs2LoginStatus.PASSWORD_KNOWN, "We suspect someone knows your password."},
			{Rs2LoginStatus.MEMBERS_WORLD, "You need a members' account to use this world."},
			{Rs2LoginStatus.MEMBERS_WORLD_2, "Please subscribe to login to this world."},
			{Rs2LoginStatus.FAILED_TO_COMPLETE_LOGIN, "Could not complete login."},
			{Rs2LoginStatus.SERVER_UPDATED, "The server is being updated."},
			{Rs2LoginStatus.SERVER_UPDATING, "The game servers are currently being updated."},
			{Rs2LoginStatus.TOO_MANY_ATTEMPTS, "Too many login attempts."},
			{Rs2LoginStatus.TOO_MANY_ATTEMPTS_LAUNCHER, "Too many login attempts. Use the Jagex Launcher."},
			{Rs2LoginStatus.MEMBERS_AREA, "Please move your character to a non-members area."},
			{Rs2LoginStatus.ACCOUNT_LOCKED, "Your account has been locked due to suspicious activity."},
			{Rs2LoginStatus.CLOSED_BETA, "This world is running a closed Beta."},
			{Rs2LoginStatus.INVALID_LOGIN_SERVER, "Invalid loginserver requested."},
			{Rs2LoginStatus.MALFORMED_PACKET, "Malformed login packet."},
			{Rs2LoginStatus.NO_REPLY, "No reply from loginserver."},
			{Rs2LoginStatus.ERROR_LOADING_PROFILE, "Error loading your profile."},
			{Rs2LoginStatus.UNEXPECTED_LOGIN_RESPONSE, "Unexpected loginserver response."},
			{Rs2LoginStatus.ADDRESS_BLOCKED, "This computer's address has been blocked."},
			{Rs2LoginStatus.SERVICE_UNAVAILABLE, "Service unavailable."},
			{Rs2LoginStatus.SET_DISPLAY_NAME, "Your account must have a displayname set."},
			{Rs2LoginStatus.UNSUCCESSFUL_LOGIN, "Your attempt to log into your account was unsuccessful."},
			{Rs2LoginStatus.INACCESSIBLE, "Your account is currently inaccessible."},
			{Rs2LoginStatus.VOTE, "You must vote to play on this world."},
			{Rs2LoginStatus.NOT_ELIGIBLE, "Sorry, but your account is not eligible to play."},
			{Rs2LoginStatus.ENTER_AUTH, "Enter your authenticator code."},
			{Rs2LoginStatus.BAD_AUTH_CODE, "The code you entered was incorrect."},
			{Rs2LoginStatus.UNEXPECTED_SERVER_RESPONSE, "Unexpected server response."},
			{Rs2LoginStatus.CONNECTING_TO_SERVER, "Connecting to server..."},
			{Rs2LoginStatus.TOTAL_LEVEL, "This world requires a total level of 1500."},
			{Rs2LoginStatus.TOTAL_LEVEL,
				"You need a total of 750 in non-member skills to play on this world."},
			{Rs2LoginStatus.WORLD_LOCKED, "This world is currently restricted."},
			{Rs2LoginStatus.SIGNED_OUT, "You were disconnected from the server."}
		};

		for (Object[] testCase : cases)
		{
			Rs2LoginStatus expected = (Rs2LoginStatus) testCase[0];
			String message = (String) testCase[1];
			Rs2LoginResponseClassifier.Classification result = classifier.classify(
				GameState.LOGIN_SCREEN, 2, Collections.singletonList(message));
			assertEquals(message, expected, result.getStatus());
			assertEquals(message, Rs2LoginStatusSource.RESPONSE_TEXT, result.getSource());
		}
	}

	@Test
	public void rootLoginScreenIgnoresAndClearsStaleResponseText()
	{
		String staleError = "Incorrect username or password.";
		Rs2LoginResponseClassifier.Classification classification = classifier.classify(
			GameState.LOGIN_SCREEN, 0, Collections.singletonList(staleError));

		assertEquals(Rs2LoginStatus.LOGIN_SCREEN, classification.getStatus());
		assertEquals(Rs2LoginStatusSource.LOGIN_INDEX, classification.getSource());

		LoginResponseSnapshot snapshot = Rs2LoginResponse.classifySnapshot(
			GameState.LOGIN_SCREEN, 0, Collections.singletonList(staleError), true, "test");
		assertEquals(Rs2LoginStatus.LOGIN_SCREEN, snapshot.getStatus());
		assertEquals(Collections.emptyList(), snapshot.getResponseLines());

		assertEquals(
			Rs2LoginStatus.INVALID_LOGIN,
			classifier.classify(GameState.LOGIN_SCREEN, 2, Collections.singletonList(staleError)).getStatus());
	}

	@Test
	public void normalizesTagsWhitespaceAndSplitLines()
	{
		Rs2LoginResponseClassifier.Classification result = classifier.classify(
			GameState.LOGIN_SCREEN,
			2,
			Arrays.asList("<col=ff0000>Either your account</col>", "is still   logged in,", null));

		assertEquals(Rs2LoginStatus.STILL_LOGGED_IN, result.getStatus());
	}

	@Test
	public void gameStateTakesPrecedenceOverStaleText()
	{
		assertEquals(
			Rs2LoginStatus.LOGGED_IN,
			classifier.classify(
				GameState.LOGGED_IN, -1, Collections.singletonList("Incorrect username or password."))
				.getStatus());
		assertEquals(
			Rs2LoginStatus.CONNECTING_TO_SERVER,
			classifier.classify(
				GameState.LOGGING_IN, -1, Collections.singletonList("Incorrect username or password."))
				.getStatus());
	}

	@Test
	public void classifiesPageAndLifecycleStates()
	{
		assertEquals(
			Rs2LoginStatus.ENTER_AUTH,
			classifier.classify(GameState.LOGIN_SCREEN_AUTHENTICATOR, 4, Collections.emptyList()).getStatus());
		assertEquals(
			Rs2LoginStatus.ENTER_AUTH,
			classifier.classify(GameState.LOGIN_SCREEN, 4, Collections.emptyList()).getStatus());
		assertEquals(
			Rs2LoginStatus.LOGIN_SCREEN,
			classifier.classify(GameState.LOGIN_SCREEN, 2, Collections.emptyList()).getStatus());
		assertEquals(
			Rs2LoginStatus.SIGNED_OUT,
			classifier.classify(GameState.CONNECTION_LOST, -1, Collections.emptyList()).getStatus());
		assertEquals(
			Rs2LoginStatus.UNKNOWN,
			classifier.classify(GameState.UNKNOWN, -1, Collections.emptyList()).getStatus());
	}
}
