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

	/**
	 * 重试耗尽后的冷却时长。耗尽意味着“走近也解决不了”，此时继续每 tick 重试只会反复走位、
	 * 反复点击并刷日志；冷却期内直接放弃恢复，交给脚本自身的失败处理。
	 */
	private static final long DEFAULT_COOLDOWN_MILLIS = 30_000L;

	/** 冷却截止时间（毫秒时间戳）。0 表示从未进入冷却。 */
	private static volatile long cooldownUntil = 0L;

	private CantReachTargetRecovery()
	{
	}

	public static boolean shouldStart(boolean detectionEnabled, boolean cantReachTarget)
	{
		return detectionEnabled && cantReachTarget && RECOVERY_DEPTH.get() == 0 && !isCoolingDown();
	}

	/** 冷却期内不再启动恢复。 */
	public static boolean isCoolingDown()
	{
		return System.currentTimeMillis() < cooldownUntil;
	}

	public static void cooldown()
	{
		cooldown(DEFAULT_COOLDOWN_MILLIS);
	}

	public static void cooldown(long millis)
	{
		cooldownUntil = System.currentTimeMillis() + Math.max(0L, millis);
	}

	/**
	 * 放弃本轮恢复：清掉全局 can't-reach 标志与重试计数，记一条日志，然后进入冷却。
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
			+ " after " + retries + " attempts; cooling down for " + (DEFAULT_COOLDOWN_MILLIS / 1000) + "s");
		Microbot.cantReachTarget = false;
		Microbot.cantReachTargetRetries = 0;
		cooldown();
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
