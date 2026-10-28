package net.runelite.client.plugins.microbot.util.walker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/**
 * 原版寻路日志总开关（{@code util.walker} 与 {@code shortestpath} 两棵 logger 子树）。
 * <p>
 * 关闭（默认）时，INFO/WARN 的寻路日志（{@code [WebWalk]}、{@code [Walker]}、
 * {@code [WalkerTelemetry]}、pathfinder 诊断等）不再输出，也不会被 {@code GameChatAppender}
 * 镜像到游戏聊天框；真正的 ERROR 仍然保留。开启时输出 DEBUG 级完整细节。
 * <p>
 * 脚本可以在 {@code run()} 里决定本次执行是否输出寻路日志：
 * <pre>{@code
 * Rs2Walker.setWalkerLoggingEnabled(false); // 安静执行
 * Rs2Walker.setWalkerLoggingEnabled(true);  // 排查问题时打开
 * }</pre>
 * <p>
 * 实现说明：除了调整 logger 级别，还挂了一个 logback {@link TurboFilter}。
 * 因为其它代码（例如 FST walker 的启动流程）会把自己认定的级别写回这两个包，
 * 单靠 {@code setLevel} 会被覆盖；过滤器在级别判断之前生效，保证"关闭"始终有效。
 */
public final class WalkerLogSwitch {
    /**
     * 开关自身的 logger 名不在受控前缀内，保证"已开启"这条提示始终可见。
     */
    private static final Logger LOG = LoggerFactory.getLogger("Microbot.WalkerLog");

    /**
     * 受开关控制的 logger 前缀。logback 的 logger 名按点分层级，
     * 设置父名前缀的级别即可覆盖其下所有子 logger（除非子 logger 被单独设过级别）。
     */
    private static final String[] WALKER_LOGGER_PREFIXES = {
            // 原版 walker：Rs2Walker / Rs2WalkerMovement / Rs2WalkerDoors / WebWalkLog 等
            "net.runelite.client.plugins.microbot.util.walker",
            // 原版最短路径：Pathfinder / PathfinderConfig / ShortestPathPlugin 等
            "net.runelite.client.plugins.microbot.shortestpath",
    };

    /** 关闭时保留的最低级别：只让真正的错误通过。 */
    private static final Level OFF_LEVEL = Level.ERROR;

    /** 开启时输出的最详细级别。 */
    private static final Level ON_LEVEL = Level.DEBUG;

    /** 默认关闭：寻路日志是排查用的，不是常态输出。 */
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);

    private WalkerLogSwitch() {
    }

    /**
     * @return 当前是否输出寻路日志
     */
    public static boolean isEnabled() {
        return ENABLED.get();
    }

    /**
     * 打开或关闭寻路日志。可以随时调用，立即生效。
     *
     * @param enabled true=输出完整寻路日志（DEBUG）；false=只保留 ERROR
     */
    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
        apply(enabled);
        if (enabled) {
            LOG.info("[WalkerLog] 寻路日志已开启（{}）", ON_LEVEL);
        } else {
            // 关闭是常态，不打 INFO，避免又把控制台弄响
            LOG.debug("[WalkerLog] 寻路日志已关闭（{}）", OFF_LEVEL);
        }
    }

    /** 打开寻路日志。 */
    public static void enable() {
        setEnabled(true);
    }

    /** 关闭寻路日志。 */
    public static void disable() {
        setEnabled(false);
    }

    /**
     * 按当前开关状态重新应用一次（级别 + 过滤器）。
     * 适合同步配置项，或在别处改动过这些 logger 之后调用。
     */
    public static void apply() {
        apply(ENABLED.get());
    }

    private static void apply(boolean enabled) {
        installFilter();

        Level level = enabled ? ON_LEVEL : OFF_LEVEL;
        for (String prefix : WALKER_LOGGER_PREFIXES) {
            Logger logger = LoggerFactory.getLogger(prefix);
            if (logger instanceof ch.qos.logback.classic.Logger) {
                ((ch.qos.logback.classic.Logger) logger).setLevel(level);
            }
        }
    }

    /**
     * 挂载一次性过滤器：开关关闭时直接丢弃受控前缀下低于 ERROR 的事件。
     * 重复调用安全，不会重复注册。
     */
    private static void installFilter() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (TurboFilter filter : context.getTurboFilterList()) {
            if (filter instanceof WalkerLogTurboFilter) {
                return;
            }
        }
        context.addTurboFilter(new WalkerLogTurboFilter());
    }

    /** 受控前缀下、开关关闭时丢弃 INFO 及以下事件。 */
    private static final class WalkerLogTurboFilter extends TurboFilter {
        @Override
        public FilterReply decide(Marker marker, ch.qos.logback.classic.Logger logger, Level level,
                                  String format, Object[] params, Throwable t) {
            if (ENABLED.get() || level == null || level.isGreaterOrEqual(Level.ERROR)) {
                return FilterReply.NEUTRAL;
            }
            String name = logger.getName();
            for (String prefix : WALKER_LOGGER_PREFIXES) {
                if (name.equals(prefix) || name.startsWith(prefix + ".")) {
                    return FilterReply.DENY;
                }
            }
            return FilterReply.NEUTRAL;
        }
    }
}
