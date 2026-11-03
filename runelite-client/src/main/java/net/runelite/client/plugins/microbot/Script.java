package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.diagnostics.FlightRecorder;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.recovery.CantReachTargetRecovery;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for Microbot automation scripts.
 * Provides scheduling helpers, guards against client-thread misuse, and common shutdown/reset logic.
 */
@Slf4j
public abstract class Script extends Global implements IScript {
    static final long EXECUTOR_KEEP_ALIVE_SECONDS = 60;
    private final AtomicBoolean startAnnounced = new AtomicBoolean();

    private void announceStart() {
        if (Microbot.getEventBus() != null && startAnnounced.compareAndSet(false, true)) {
            Microbot.getEventBus().post(new ScriptStarted(this));
        }
    }

    /**
     * One start/stop cycle of a script. Every task scheduled through {@link #scheduledExecutorService}
     * while the cycle is open belongs to it, so stopping the script stops all of them - including
     * the ones the script scheduled itself and never tracked in a field.
     */
    private static final class Batch
    {
        private final long id;
        private final Set<ScheduledFuture<?>> futures = ConcurrentHashMap.newKeySet();
        private volatile boolean closed;

        private Batch(long id)
        {
            this.id = id;
        }
    }

    /**
     * Thrown inside a task whose batch was closed. A periodic task that fails this way is not run
     * again, which is how tasks of a previous start/stop cycle are retired.
     */
    private static final class BatchClosedException extends RuntimeException
    {
        private BatchClosedException()
        {
            super(null, null, false, false);
        }
    }

    private final AtomicLong batchCounter = new AtomicLong();
    private final AtomicReference<Batch> currentBatch = new AtomicReference<>();
    private final ThreadLocal<Batch> runningBatch = new ThreadLocal<>();

    private final ScheduledThreadPoolExecutor scriptExecutor = new ScheduledThreadPoolExecutor(10,
        new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r);
                t.setName(Script.this.getClass().getSimpleName() + "-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        }) {
            {
                setKeepAliveTime(EXECUTOR_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
                allowCoreThreadTimeOut(true);
                setRemoveOnCancelPolicy(true);
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                    long delay, TimeUnit unit) {
                // Announce before a zero-delay loop can report a terminal supply failure.
                announceStart();
                Batch batch = openBatch();
                return track(batch,
                        super.scheduleWithFixedDelay(guard(batch, command), initialDelay, delay, unit));
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                    long period, TimeUnit unit) {
                announceStart();
                Batch batch = openBatch();
                return track(batch,
                        super.scheduleAtFixedRate(guard(batch, command), initialDelay, period, unit));
            }

            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                Batch batch = openBatch();
                return track(batch, super.schedule(guard(batch, command), delay, unit));
            }
        };

    protected ScheduledExecutorService scheduledExecutorService = scriptExecutor;
    protected ScheduledFuture<?> scheduledFuture;
    protected ScheduledFuture<?> mainScheduledFuture;

    protected Script() {
        ScriptLifecycleRegistry.register(this);
    }

    /**
     * Returns the batch new tasks join. A task scheduled from inside a script task belongs to the
     * batch of that task, so a task of a closed cycle cannot revive work in a later one. A batch is
     * opened again only when the script is started again.
     */
    private Batch openBatch() {
        Batch callerBatch = runningBatch.get();
        if (callerBatch != null) {
            return callerBatch;
        }
        Batch batch = currentBatch.get();
        if (batch == null || batch.closed) {
            batch = new Batch(batchCounter.incrementAndGet());
            currentBatch.set(batch);
            log.debug("Script {} started batch {}", getClass().getSimpleName(), batch.id);
        }
        return batch;
    }

    private ScheduledFuture<?> track(Batch batch, ScheduledFuture<?> future) {
        batch.futures.add(future);
        if (batch.closed) {
            // The batch was closed while the task was being submitted.
            future.cancel(false);
        }
        return future;
    }

    /**
     * Wraps a task so that it stops as soon as its batch is closed, and so that the batch it
     * belongs to can be resolved from inside the task.
     */
    private Runnable guard(Batch batch, Runnable command) {
        return () -> {
            Batch previous = runningBatch.get();
            runningBatch.set(batch);
            try {
                if (batch.closed) {
                    throw new BatchClosedException();
                }
                command.run();
            } finally {
                runningBatch.set(previous);
            }
        };
    }

    /**
     * Indicates whether the main scheduled script loop is still active.
     */
    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }

    @Getter
    protected static WorldPoint initialPlayerLocation;

    /**
     * Cancel scheduled tasks, clear shared state, and reset helpers.
     * Safe to call multiple times; no-ops if already shut down.
     *
     * <p>Only the batch the caller belongs to is closed. A task of an earlier start/stop cycle that
     * calls this from its error handler therefore cannot stop the cycle that is running now.</p>
     */
    public void shutdown() {
        Batch batch = runningBatch.get();
        if (batch == null) {
            batch = currentBatch.get();
        }
        if (batch == null) {
            startAnnounced.set(false);
            return;
        }
        if (batch.closed) {
            // Already stopped, or a task of an earlier cycle calling shutdown: leave the running
            // cycle alone.
            return;
        }

        boolean hadLiveTasks = batch.futures.stream().anyMatch(future -> !future.isDone());
        batch.closed = true;
        // Cancel with interrupt: a script spends most of its time inside one call - a web walk can
        // run for minutes - and cancelling without interrupting would let that call run to the end,
        // so the character keeps moving long after the script was stopped. The interrupt ends the
        // running call right away. It cannot stop a later cycle: the interrupted task resolves to
        // this batch, which is already closed, so its error handler is a no-op.
        for (ScheduledFuture<?> future : batch.futures) {
            future.cancel(true);
        }
        batch.futures.clear();

        if (batch != currentBatch.get()) {
            // A task of an earlier cycle: retire that cycle only.
            return;
        }
        currentBatch.set(null);
        startAnnounced.set(false);
        ScriptHeartbeatRegistry.remove(this.getClass().getName());
        // A pending client thread task - a path calculation for example - would keep the character
        // moving after the script was stopped, so it is cancelled as well.
        ClientThread clientThread = Microbot.getClientThread();
        if (clientThread != null) {
            clientThread.cancelScheduledTask(this);
        }
        if (hadLiveTasks) {
            resetSharedState();
        }
    }

    /**
     * Releases the state a running script shares with the rest of the client.
     */
    private void resetSharedState() {
        try {
            Rs2PathApi.exit();
            initialPlayerLocation = null;
            // Backstop for a script stopped between a hold and its release. Fires inconsistently:
            // most scripts catch-and-continue without reaching here.
            Rs2Keyboard.releaseHeldKeys();
            Microbot.pauseAllScripts.set(false);
            CantReachTargetRecovery.reset();
            Rs2Walker.disableTeleports = false;
            Microbot.getSpecialAttackConfigs().reset();
        } catch (Throwable ex) {
            log.warn("Error resetting shared state for script {}", getClass().getSimpleName(), ex);
        }
    }

    /**
     * Waits until no task of this script is running any more.
     *
     * @return {@code true} when the executor went idle within the timeout
     */
    public boolean awaitIdle(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        do {
            if (scriptExecutor.getActiveCount() == 0) {
                return true;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.nanoTime() < deadline);
        return scriptExecutor.getActiveCount() == 0;
    }

    /**
     * Permanently releases this script when its defining class loader is unloaded.
     * Normal plugin disable/enable cycles must continue to use {@link #shutdown()}.
     */
    final boolean disposeForClassLoaderUnload(long timeoutMillis) {
        ScriptHeartbeatRegistry.remove(this.getClass().getName());
        try {
            shutdown();
        } catch (ThreadDeath ex) {
            throw ex;
        } catch (Throwable ex) {
            log.warn("Error shutting down script {} during unload", getClass().getSimpleName(), ex);
        }

        // The shutdown above already interrupted the running task; this is the grace period for it
        // to unwind and release the executor.
        awaitIdle(Math.max(1L, Math.min(timeoutMillis / 4, 500L)));

        scheduledExecutorService.shutdownNow();
        try {
            return scheduledExecutorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Default pre-loop guard invoked by script schedulers.
     * Returns {@code false} to pause a loop when a blocking event is executing, scripts are paused,
     * tutorial island is incomplete, or the current thread is interrupted.
     * When logged in, it also turns run on if auto-run is enabled, energy is above
     * {@link Microbot#runEnergyThreshold} and no blocking interface is open (see {@link #isBlockingInterfaceOpen()}).
     */
    public boolean run() {
        ScriptHeartbeatRegistry.recordHeartbeat(this.getClass().getName());

        if (Microbot.isLoggedIn() && !SessionFatigue.isActive()) {
            SessionFatigue.startSession();
        }

        if (Microbot.isLoggedIn() && !Rs2Player.hasCompletedTutorialIsland())
            return true;

        if (Rs2Player.hasCompletedTutorialIsland() && Microbot.getBlockingEventManager().shouldBlockAndProcess()) {
            // A blocking event was found & is executing
            return false;
        }
        // The arbiter keeps its own flag, so a takeover idles every script through the gate that
        // already exists, cancelling nothing.
        boolean humanOwnsInput = InputArbiter.isHuman();
        if (humanOwnsInput) {
            // A held key is not gesture-scoped, so InputLoop cannot unwind it.
            Rs2Keyboard.releaseHeldKeys();
        }
        if (Microbot.pauseAllScripts.get() || humanOwnsInput)
            return false;
        if (Thread.currentThread().isInterrupted())
            return false;

        if (Microbot.isLoggedIn()) {
            boolean hasRunEnergy = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getEnergy()).orElse(0) > Microbot.runEnergyThreshold;
            // Clicking the run orb mid-interface steals focus from the script's own dialogue/bank/production flow.
            if (Microbot.enableAutoRunOn && hasRunEnergy && !isBlockingInterfaceOpen())
                Rs2Player.toggleRunEnergy(true);
            if (!hasRunEnergy && Microbot.useStaminaPotsIfNeeded && Rs2Player.isMoving()) {
                Rs2Inventory.useRestoreEnergyItem();
            }
            Microbot.getConfigManager().setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyEnableAutoRunOn, Microbot.enableAutoRunOn);
            Microbot.getConfigManager().setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyUseStaminaPotsIfNeeded, Microbot.useStaminaPotsIfNeeded);
        }
        return true;
    }

    protected void trace(String key, Object value) {
        String name = getClass().getName();
        FlightRecorder.trace(name.substring(name.lastIndexOf('.') + 1) + "." + key, value);
    }

    /**
     * Interfaces during which the automatic run toggle must not click: chat dialogues, banks, shops,
     * the Grand Exchange and skilling/production menus.
     */
    private static final int[] AUTO_RUN_BLOCKING_INTERFACES = {
            InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.CHATMENU,
            InterfaceID.OBJECTBOX, InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.LEVELUP_DISPLAY,
            InterfaceID.BANKMAIN, InterfaceID.BANKPIN_KEYPAD, InterfaceID.BANK_DEPOSITBOX,
            InterfaceID.SHOPMAIN, InterfaceID.GE_OFFERS,
            InterfaceID.SKILLMULTI, InterfaceID.SMITHING, InterfaceID.CRAFTING_GOLD, InterfaceID.SILVER_CRAFTING
    };

    /**
     * Checks, in one client-thread call, whether any interface in {@link #AUTO_RUN_BLOCKING_INTERFACES}
     * has a visible root widget.
     *
     * @return {@code true} if one is open, or if the client thread couldn't answer (so auto-run skips the click)
     */
    static boolean isBlockingInterfaceOpen() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (int groupId : AUTO_RUN_BLOCKING_INTERFACES) {
                Widget root = Microbot.getClient().getWidget(groupId, 0);
                if (root != null && !root.isHidden()) return true;
            }
            return false;
        }).orElse(true);
    }
}
