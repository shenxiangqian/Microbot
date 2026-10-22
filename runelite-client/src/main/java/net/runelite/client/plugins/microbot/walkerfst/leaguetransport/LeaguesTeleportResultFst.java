package net.runelite.client.plugins.microbot.walkerfst.leaguetransport;

import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Nullable;

public final class LeaguesTeleportResultFst
{
	private final boolean success;
	private final @Nullable LeaguesTeleportFailureReasonFst failureReason;
	private final String message;
	private final LeaguesRegionFst target;
	private final EnumSet<LeaguesRegionFst> unlockedRegionsSnapshot;

	private LeaguesTeleportResultFst(
			boolean success,
			@Nullable LeaguesTeleportFailureReasonFst failureReason,
			String message,
			LeaguesRegionFst target,
			EnumSet<LeaguesRegionFst> unlockedRegionsSnapshot)
	{
		this.success = success;
		this.failureReason = success
				? null
				: (failureReason != null ? failureReason : LeaguesTeleportFailureReasonFst.UNKNOWN);
		this.message = message != null ? message : "";
		this.target = target;
		this.unlockedRegionsSnapshot = unlockedRegionsSnapshot != null ? EnumSet.copyOf(unlockedRegionsSnapshot) : null;
	}

	public static LeaguesTeleportResultFst ok(LeaguesRegionFst target, EnumSet<LeaguesRegionFst> unlockedRegionsSnapshot)
	{
		return new LeaguesTeleportResultFst(true, null, "", target, unlockedRegionsSnapshot);
	}

	public static LeaguesTeleportResultFst failure(
			LeaguesTeleportFailureReasonFst reason,
			String message,
			LeaguesRegionFst target,
			EnumSet<LeaguesRegionFst> unlockedRegionsSnapshot)
	{
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(message, "message");
		if (message.isEmpty())
		{
			throw new IllegalArgumentException("failure message must not be empty");
		}
		return new LeaguesTeleportResultFst(false, reason, message, target, unlockedRegionsSnapshot);
	}

	public boolean isSuccess()
	{
		return success;
	}

	/**
	 * @return failure reason when {@link #isSuccess()} is {@code false}; {@code null} on success (not {@link LeaguesTeleportFailureReason#UNKNOWN}).
	 */
	public @Nullable LeaguesTeleportFailureReasonFst getFailureReason()
	{
		return failureReason;
	}

	public String getMessage()
	{
		return message;
	}

	public LeaguesRegionFst getTarget()
	{
		return target;
	}

	public EnumSet<LeaguesRegionFst> getUnlockedRegionsSnapshot()
	{
		return unlockedRegionsSnapshot != null ? EnumSet.copyOf(unlockedRegionsSnapshot) : null;
	}
}

