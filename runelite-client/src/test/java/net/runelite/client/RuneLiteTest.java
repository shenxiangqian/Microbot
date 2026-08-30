/*
 * Copyright (c) 2026 Microbot
 * All rights reserved.
 */
package net.runelite.client;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Preferences;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuneLiteTest
{
	@Test
	public void testDisableGameSounds()
	{
		Client client = mock(Client.class);
		Preferences preferences = mock(Preferences.class);
		when(client.getPreferences()).thenReturn(preferences);

		assertTrue(RuneLite.disableGameSounds(client));
		verify(client).setMusicVolume(0);
		verify(preferences).setSoundEffectVolume(0);
		verify(preferences).setAreaSoundEffectVolume(0);
	}

	@Test
	public void testSensitiveArgumentsAreRedacted()
	{
		String[] arguments = {
			"--session-id", "secret-session",
			"--character-id=123456",
			"--proxy", "socks5://user:password@example.test:1080",
			"--proxy-user", "user",
			"--proxy-pass=password",
			"--accounts-root", "C:\\MicrobotAccounts",
			"--index", "12"
		};

		assertEquals(
			"--session-id *** --character-id=*** --proxy *** --proxy-user *** --proxy-pass=*** --accounts-root *** --index 12",
			RuneLite.formatArgumentsForLog(arguments));
	}

	@Test
	public void testEmptyArgumentsAreLoggedAsNone()
	{
		assertEquals("none", RuneLite.formatArgumentsForLog(new String[0]));
	}

	@Test
	public void testSensitiveJvmArgumentsAreRedacted()
	{
		List<String> arguments = List.of(
			"-Xmx1g",
			"-Duser.home=C:\\MicrobotAccounts\\123456",
			"-DJX_SESSION_ID=secret-session",
			"-Dordinary.property=value");

		assertEquals(
			"-Xmx1g -Duser.home=*** -DJX_SESSION_ID=*** -Dordinary.property=value",
			RuneLite.formatJvmArgumentsForLog(arguments));
	}
}
