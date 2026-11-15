/*
 * Copyright (c) 2026, Microbot
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.offeraallfirst;

import com.google.inject.Provides;
import java.util.Locale;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Offer All First",
	description = "把右键菜单中的 Offer All 选项移到菜单最顶部",
	tags = {"trade", "menu", "inventory"},
	enabledByDefault = true
)
public class OfferAllFirstPlugin extends Plugin
{
	private static final String OFFER_ALL_OPTION = "offerall";

	@Inject
	private Client client;

	@Inject
	private OfferAllFirstConfig config;

	@Provides
	OfferAllFirstConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OfferAllFirstConfig.class);
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		// 菜单已经打开时不会被重建，此时再交换会让顺序反复横跳
		if (client.isMenuOpen())
		{
			return;
		}

		if (config.onlyInTrade() && !isTradeInterfaceOpen())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();

		int offerAllIndex = findOfferAllIndex(entries);
		if (offerAllIndex < 0 || offerAllIndex == entries.length - 1)
		{
			return;
		}

		moveToTop(entries, offerAllIndex);
		menu.setMenuEntries(entries);
	}

	/**
 * 客户端的菜单数组是自底向上排列的，数组末尾的条目对应菜单最顶部，
	 * 所以把目标条目移到末尾即可让它排在所有选项之前。
	 */
	private static void moveToTop(MenuEntry[] entries, int index)
	{
		MenuEntry offerAllEntry = entries[index];

		for (int current = index; current < entries.length - 1; current++)
		{
			entries[current] = entries[current + 1];
		}

		entries[entries.length - 1] = offerAllEntry;
	}

	private static int findOfferAllIndex(MenuEntry[] entries)
	{
		for (int index = 0; index < entries.length; index++)
		{
			if (OFFER_ALL_OPTION.equals(normalizeOption(entries[index].getOption())))
			{
				return index;
			}
		}

		return -1;
	}

	/**
 * 去掉颜色标签与空白字符，并忽略空格与连字符，
	 * 使 "Offer All"、"offer-all" 等写法都能被识别。
	 */
	private static String normalizeOption(String option)
	{
		if (option == null)
		{
			return "";
		}

		return Text.removeTags(option)
			.toLowerCase(Locale.ROOT)
			.replace(" ", "")
			.replace("-", "");
	}

	private boolean isTradeInterfaceOpen()
	{
		return isInterfaceOpen(InterfaceID.TRADECONFIRM)
			|| isInterfaceOpen(InterfaceID.TRADEMAIN)
			|| isInterfaceOpen(InterfaceID.TRADESIDE);
	}

	private boolean isInterfaceOpen(int interfaceId)
	{
		return client.getWidget(interfaceId, 0) != null;
	}
}
