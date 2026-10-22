package net.runelite.client.plugins.microbot.walkerfst;

/** Stable Microbot-owned reason why route planning stopped. */
public enum Rs2RouteTerminationFst
{
	TARGET_REACHED,
	SEARCH_EXHAUSTED,
	CUTOFF_REACHED,
	CANCELLED,
	FAILED
}
