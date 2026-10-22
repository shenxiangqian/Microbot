package net.runelite.client.plugins.microbot.walkerfst.shortestpath;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TileStyleFst {
	TILES("Tiles"),
	LINES("Lines");

	private final String type;

	public static TileStyleFst fromType(String type) {
		for (TileStyleFst tileStyle : values()) {
			if (tileStyle.type.equals(type)) {
				return tileStyle;
			}
		}
		return null;
	}
}