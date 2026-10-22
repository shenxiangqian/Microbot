package net.runelite.client.plugins.microbot.walkerfst.shortestpath;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TileCounterFst {
	DISABLED("Disabled"),
	TRAVELLED("Travelled"),
	REMAINING("Remaining");

	private final String type;

	public static TileCounterFst fromType(String type) {
		for (TileCounterFst tileCounter : values()) {
			if (tileCounter.type.equals(type)) {
				return tileCounter;
			}
		}
		return null;
	}
}