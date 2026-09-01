/*
 * Copyright (c) 2026 Microbot
 * All rights reserved.
 */
package net.runelite.client.plugins.lowmemory;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.name.Names;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LowMemoryPluginTest
{
	@Test
	public void testCommandLineModeOverridesSavedConfiguration()
	{
		Client client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		LowMemoryConfig config = mock(LowMemoryConfig.class);
		when(config.lowDetail()).thenReturn(false);
		when(config.hideLowerPlanes()).thenReturn(false);

		LowMemoryPlugin plugin = new LowMemoryPlugin();
		Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(client);
				bind(ClientThread.class).toInstance(clientThread);
				bind(LowMemoryConfig.class).toInstance(config);
				bindConstant().annotatedWith(Names.named("lowDetailMode")).to(true);
			}
		}).injectMembers(plugin);

		assertTrue(plugin.useLowDetail());
		assertTrue(plugin.hideLowerPlanes());
	}
}
