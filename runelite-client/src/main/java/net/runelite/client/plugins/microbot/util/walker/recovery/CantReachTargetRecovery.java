package net.runelite.client.plugins.microbot.util.walker.recovery;

import java.util.Objects;
import java.util.function.BiPredicate;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Owns the nested walker call used to recover a failed object or NPC interaction.
 *
 * <p>The legacy walker may need to interact with a closed door while approaching the original
 * interaction target. The global can't-reach flag must remain set until that approach succeeds,
 * but it must not recursively start another global recovery from the door interaction on the
 * same thread.</p>
 */
public final class CantReachTargetRecovery
{
	private static final ThreadLocal<Integer> RECOVERY_DEPTH = ThreadLocal.withInitial(() -> 0);

	private CantReachTargetRecovery()
	{
	}

	public static boolean shouldStart(boolean detectionEnabled, boolean cantReachTarget)
	{
		return detectionEnabled && cantReachTarget && RECOVERY_DEPTH.get() == 0;
	}

	/**
	 * 放弃本轮恢复：清掉全局 can't-reach 标志与重试计数，记一条日志。
	 *
	 * <p>这里刻意不弹模态对话框，也不设置 {@code Microbot.pauseAllScripts}：那两个副作用会把
	 * 整个机器人和脚本线程一起冻住，等人工处理，而一次物体交互失败并不值得全局停摆。</p>
	 *
	 * @param targetDescription 目标描述，仅用于日志
	 * @param retries           已消耗的重试次数，仅用于日志
	 */
	public static void abandon(String targetDescription, int retries)
	{
		Microbot.log("[Interact] can't-reach recovery abandoned for " + targetDescription
			+ " after " + retries + " attempts");
		Microbot.cantReachTarget = false;
		Microbot.cantReachTargetRetries = 0;
	}

	public static boolean retryExhausted(int retries, int retryLimit)
	{
		return retries >= retryLimit;
	}

	public static boolean walkTo(WorldPoint target, int distance)
	{
		return walkTo(target, distance, Rs2Walker::walkTo);
	}

	static boolean walkTo(WorldPoint target, int distance, BiPredicate<WorldPoint, Integer> walker)
	{
		Objects.requireNonNull(walker, "recovery walker");
		int previousDepth = RECOVERY_DEPTH.get();
		RECOVERY_DEPTH.set(previousDepth + 1);
		try
		{
			return walker.test(target, distance);
		}
		finally
		{
			if (previousDepth == 0)
			{
				RECOVERY_DEPTH.remove();
			}
			else
			{
				RECOVERY_DEPTH.set(previousDepth);
			}
		}
	}
}
