package net.runelite.client.plugins.microbot.util.security.login;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Public entry point for detailed login-state inspection. */
public final class Rs2LoginResponse
{
	private static final LoginResponseReflectionReader READER = new LoginResponseReflectionReader();
	private static final Rs2LoginResponseClassifier CLASSIFIER = new Rs2LoginResponseClassifier();

	private Rs2LoginResponse()
	{
	}

	public static Rs2LoginStatus getStatus()
	{
		return getSnapshot().getStatus();
	}

	public static LoginResponseSnapshot getSnapshot()
	{
		return getSnapshot(Microbot.getClient());
	}

	public static LoginResponseSnapshot getSnapshot(Client client)
	{
		if (client == null)
		{
			return unavailableSnapshot();
		}

		ClientThread clientThread = Microbot.getClientThread();
		if (clientThread == null)
		{
			return unavailableSnapshot();
		}

		Optional<RawSnapshot> raw = clientThread.runOnClientThreadOptional(() ->
		{
			GameState gameState = client.getGameState();
			int loginIndex = isLoginScreen(gameState) ? client.getLoginIndex() : -1;
			LoginResponseReflectionReader.ReadResult readResult = isLoginScreen(gameState)
				? READER.readOnClientThread(client)
				: LoginResponseReflectionReader.ReadResult.unavailable(READER.getMappingVersion());
			return new RawSnapshot(gameState, loginIndex, readResult);
		});
		if (raw.isEmpty())
		{
			return unavailableSnapshot();
		}

		RawSnapshot value = raw.get();
		return classifySnapshot(
			value.gameState,
			value.loginIndex,
			value.readResult.getLines(),
			value.readResult.isAvailable(),
			value.readResult.getMappingVersion());
	}

	/**
	 * Pure classification entry point for cached observations and unit tests.
	 * It does not read live client state.
	 */
	public static LoginResponseSnapshot classifySnapshot(
		GameState gameState,
		int loginIndex,
		List<String> responseLines,
		boolean responseTextAvailable,
		String mappingVersion)
	{
		List<String> safeLines = responseLines == null ? Collections.emptyList() : responseLines;
		if (gameState == GameState.LOGIN_SCREEN && loginIndex == 0)
		{
			// The client keeps the previous response strings after returning to the root login page.
			safeLines = Collections.emptyList();
		}
		Rs2LoginResponseClassifier.Classification classification =
			CLASSIFIER.classify(gameState, loginIndex, safeLines);
		return new LoginResponseSnapshot(
			classification.getStatus(),
			gameState == null ? GameState.UNKNOWN : gameState,
			loginIndex,
			safeLines,
			classification.getSource(),
			Instant.now(),
			responseTextAvailable,
			mappingVersion);
	}

	private static boolean isLoginScreen(GameState gameState)
	{
		return gameState == GameState.LOGIN_SCREEN || gameState == GameState.LOGIN_SCREEN_AUTHENTICATOR;
	}

	private static LoginResponseSnapshot unavailableSnapshot()
	{
		return classifySnapshot(
			GameState.UNKNOWN,
			-1,
			Collections.emptyList(),
			false,
			READER.getMappingVersion());
	}

	private static final class RawSnapshot
	{
		private final GameState gameState;
		private final int loginIndex;
		private final LoginResponseReflectionReader.ReadResult readResult;

		private RawSnapshot(
			GameState gameState,
			int loginIndex,
			LoginResponseReflectionReader.ReadResult readResult)
		{
			this.gameState = gameState;
			this.loginIndex = loginIndex;
			this.readResult = readResult;
		}
	}
}
