package net.runelite.client.plugins.microbot.walkerfst;

import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.ShortestPathConfigFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.ShortestPathPluginFst;

/**
 * walkerfst 的无界面启动入口。
 * <p>
 * {@link ShortestPathPluginFst} 不再是 RuneLite 插件，不会被插件管理器启动，
 * 因此 {@link Rs2WalkerFst} 的配置和寻路状态需要在这里初始化。
 * 这里只初始化 walker 运行所需的部分，不创建面板、overlay 和热键。
 * <p>
 * 首次调用 walker 时由 {@link Rs2WalkerFst} 懒加载触发，调用线程必须是脚本线程，不能是客户端线程。
 */
@Slf4j
public final class WalkerFstBootstrap
{
	/** 启动失败后的重试间隔，避免每次调用都重复加载大量资源 */
	private static final long RETRY_COOLDOWN_MS = 10_000L;

	private static final Object LOCK = new Object();

	private static volatile boolean started;
	private static boolean starting;
	private static long lastFailureAtMs;

	private static ShortestPathPluginFst plugin;
	private static EventBus eventBus;
	private static Events events;

	private WalkerFstBootstrap()
	{
	}

	/**
	 * 确保 walker 已初始化。已初始化时直接返回 true，不加锁。
	 *
	 * @return 初始化完成返回 true；在客户端线程调用、客户端尚未就绪或启动失败时返回 false
	 */
	public static boolean ensureStarted()
	{
		if (started)
		{
			return true;
		}
		synchronized (LOCK)
		{
			if (started)
			{
				return true;
			}
			if (starting)
			{
				// 同一线程在启动过程中重入，避免递归
				return false;
			}
			if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread())
			{
				log.warn("[WalkerFst] 不能在客户端线程初始化 walker，请在脚本线程调用");
				return false;
			}
			if (System.currentTimeMillis() - lastFailureAtMs < RETRY_COOLDOWN_MS && lastFailureAtMs != 0L)
			{
				return false;
			}

			starting = true;
			try
			{
				start();
				started = true;
				log.info("[WalkerFst] 初始化完成");
				return true;
			}
			catch (Throwable t)
			{
				lastFailureAtMs = System.currentTimeMillis();
				log.error("[WalkerFst] 初始化失败，{} 秒后可重试", RETRY_COOLDOWN_MS / 1000, t);
				cleanupAfterFailure();
				return false;
			}
			finally
			{
				starting = false;
			}
		}
	}

	private static void start()
	{
		Injector injector = Microbot.getInjector();
		if (injector == null)
		{
			throw new IllegalStateException("Injector 尚未就绪");
		}

		ConfigManager configManager = injector.getInstance(ConfigManager.class);
		ShortestPathConfigFst config = configManager.getConfig(ShortestPathConfigFst.class);

		// 与 RuneLite 插件管理器的做法一致：把插件实例和配置绑定到子注入器，由注入器完成字段注入
		ShortestPathPluginFst instance = new ShortestPathPluginFst();
		injector.createChildInjector(binder ->
		{
			binder.bind(ShortestPathConfigFst.class).toInstance(config);
			binder.bind(ShortestPathPluginFst.class).toInstance(instance);
		});
		plugin = instance;

		instance.startUpHeadless();

		eventBus = injector.getInstance(EventBus.class);
		events = new Events(instance);
		eventBus.register(events);
	}

	private static void cleanupAfterFailure()
	{
		if (eventBus != null && events != null)
		{
			try
			{
				eventBus.unregister(events);
			}
			catch (Throwable ignored)
			{
				// 清理阶段的异常不再向外传播
			}
		}
		if (plugin != null)
		{
			try
			{
				plugin.shutDownHeadless();
			}
			catch (Throwable ignored)
			{
				// 清理阶段的异常不再向外传播
			}
		}
		plugin = null;
		eventBus = null;
		events = null;
	}

	/**
	 * 关闭 walker 并注销事件订阅。关闭后下一次调用 {@link #ensureStarted()} 会重新初始化。
	 */
	public static void shutdown()
	{
		synchronized (LOCK)
		{
			if (!started)
			{
				return;
			}
			started = false;
			lastFailureAtMs = 0L;
			cleanupAfterFailure();
			Rs2WalkerFst.setConfig(null);
		}
	}

	/**
	 * 只订阅 walker 运行必需的事件。
	 * 菜单、插件消息等面向界面的事件不订阅，避免与原 Web Walker 插件重复处理。
	 */
	public static final class Events
	{
		private final ShortestPathPluginFst target;

		Events(ShortestPathPluginFst target)
		{
			this.target = target;
		}

		@Subscribe
		public void onGameTick(GameTick event)
		{
			target.onGameTick(event);
		}

		@Subscribe
		public void onGameStateChanged(GameStateChanged event)
		{
			target.onGameStateChanged(event);
		}

		@Subscribe
		public void onConfigChanged(ConfigChanged event)
		{
			target.onConfigChanged(event);
		}

		@Subscribe
		public void onGameObjectSpawned(GameObjectSpawned event)
		{
			target.onGameObjectSpawned(event);
		}

		@Subscribe
		public void onGameObjectDespawned(GameObjectDespawned event)
		{
			target.onGameObjectDespawned(event);
		}

		@Subscribe
		public void onWallObjectSpawned(WallObjectSpawned event)
		{
			target.onWallObjectSpawned(event);
		}

		@Subscribe
		public void onWallObjectDespawned(WallObjectDespawned event)
		{
			target.onWallObjectDespawned(event);
		}
	}
}
