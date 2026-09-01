package net.runelite.client.plugins.microbot;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MicrobotGameTickTrackingTest
{
	@After
	public void tearDown()
	{
		Microbot.clearLastGameTickTime();
	}

	@Test
	public void timestampIsAvailableOnlyAfterAnInGameTick()
	{
		assertEquals(0L, Microbot.getLastGameTickTime());
		assertEquals(0L, Microbot.getMillisSinceLastGameTick());

		Microbot.recordGameTick();

		assertTrue(Microbot.getLastGameTickTime() > 0L);
		assertTrue(Microbot.getMillisSinceLastGameTick() >= 0L);

		Microbot.clearLastGameTickTime();

		assertEquals(0L, Microbot.getLastGameTickTime());
		assertEquals(0L, Microbot.getMillisSinceLastGameTick());
	}

	@Test
	public void loginScreenIsNotInGame()
	{
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		assertFalse(MicrobotPlugin.isInGame(client));
	}

	@Test
	public void loggedInStateWithoutLocalPlayerIsNotInGame()
	{
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

		assertFalse(MicrobotPlugin.isInGame(client));
	}

	@Test
	public void visibleWelcomeScreenIsNotInGame()
	{
		Client client = loggedInClient();
		Widget playWidget = mock(Widget.class);
		when(client.getWidget(InterfaceID.WelcomeScreen.PLAY)).thenReturn(playWidget);
		when(playWidget.isHidden()).thenReturn(false);

		assertFalse(MicrobotPlugin.isInGame(client));
	}

	@Test
	public void hiddenWelcomeScreenIsInGame()
	{
		Client client = loggedInClient();
		Widget playWidget = mock(Widget.class);
		when(client.getWidget(InterfaceID.WelcomeScreen.PLAY)).thenReturn(playWidget);
		when(playWidget.isHidden()).thenReturn(true);

		assertTrue(MicrobotPlugin.isInGame(client));
	}

	@Test
	public void missingWelcomeScreenIsInGame()
	{
		assertTrue(MicrobotPlugin.isInGame(loggedInClient()));
	}

	private static Client loggedInClient()
	{
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		return client;
	}
}
