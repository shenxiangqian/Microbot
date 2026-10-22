package net.runelite.client.plugins.microbot.walkerfst.leaguetransport;

/**
 * Dedupe key and method normalization for {@code kind=lock-catalogue} JSONL rows.
 */
final class LeaguesTransportLockCatalogueFst
{
	private LeaguesTransportLockCatalogueFst()
	{
	}

	/**
	 * Strip optional {@code |handler=...} suffix from attempt method labels.
	 */
	static String normalizeLockCatalogueMethod(String method)
	{
		if (method == null || method.isEmpty())
		{
			return "";
		}
		int idx = method.indexOf("|handler=");
		if (idx >= 0)
		{
			return method.substring(0, idx);
		}
		return method;
	}

	static String buildDedupeKey(int packedDest, String normalizedMethod)
	{
		String m = normalizedMethod != null ? normalizedMethod : "";
		return packedDest + "|" + m;
	}
}
