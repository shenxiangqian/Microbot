package net.runelite.client.plugins.microbot;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.util.Global;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the two ways a script used to outlive its own restart: a task that the script never
 * tracked kept running after the script was stopped, and the error handler of a stopped run could
 * stop the run that replaced it.
 */
public class ScriptBatchLifecycleTest
{
    @Test public void shutdownStopsTasksTheScriptDidNotTrack() throws Exception
    {
        Fixture script = new Fixture();
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) script.scheduledExecutorService;
        try
        {
            AtomicInteger runs = new AtomicInteger();
            // Deliberately not stored in a field, so only the script's own bookkeeping can stop it.
            executor.scheduleWithFixedDelay(runs::incrementAndGet, 0, 10, TimeUnit.MILLISECONDS);
            Thread.sleep(150);
            assertTrue("the loop has to run before it can be stopped", runs.get() > 0);

            script.shutdown();
            int afterShutdown = runs.get();
            Thread.sleep(200);

            assertEquals("a task the script did not track has to stop with the script",
                    afterShutdown, runs.get());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test public void staleTaskCannotStopTheRunThatReplacedIt() throws Exception
    {
        Fixture script = new Fixture();
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) script.scheduledExecutorService;
        try
        {
            CountDownLatch staleStarted = new CountDownLatch(1);
            CountDownLatch releaseStale = new CountDownLatch(1);
            script.mainScheduledFuture = executor.scheduleWithFixedDelay(() ->
            {
                staleStarted.countDown();
                try
                {
                    releaseStale.await();
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
                // The error handler of the previous run stops the script.
                script.shutdown();
            }, 0, 10, TimeUnit.MILLISECONDS);

            assertTrue(staleStarted.await(5, TimeUnit.SECONDS));
            script.shutdown();
            releaseStale.countDown();

            CountDownLatch currentRun = new CountDownLatch(3);
            script.mainScheduledFuture = executor.scheduleWithFixedDelay(currentRun::countDown,
                    0, 10, TimeUnit.MILLISECONDS);

            assertTrue("the run that replaced the stopped one has to survive its error handler",
                    currentRun.await(5, TimeUnit.SECONDS));
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test public void stoppedScriptGoesIdleAndCanRunAgain() throws Exception
    {
        Fixture script = new Fixture();
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) script.scheduledExecutorService;
        try
        {
            CountDownLatch ran = new CountDownLatch(3);
            executor.scheduleWithFixedDelay(ran::countDown, 0, 10, TimeUnit.MILLISECONDS);
            assertTrue(ran.await(5, TimeUnit.SECONDS));

            script.shutdown();
            assertTrue("the executor has to go idle after the script was stopped",
                    awaitIdle(executor, 5000));

            CountDownLatch restarted = new CountDownLatch(3);
            executor.scheduleWithFixedDelay(restarted::countDown, 0, 10, TimeUnit.MILLISECONDS);
            assertTrue(restarted.await(5, TimeUnit.SECONDS));
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test public void scriptThatStopsItselfFromItsOwnTaskStops() throws Exception
    {
        Fixture script = new Fixture();
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) script.scheduledExecutorService;
        try
        {
            AtomicInteger runs = new AtomicInteger();
            executor.scheduleWithFixedDelay(() ->
            {
                runs.incrementAndGet();
                script.shutdown();
            }, 0, 10, TimeUnit.MILLISECONDS);

            Thread.sleep(250);
            assertEquals("the task has to stop when the script stops itself", 1, runs.get());

            CountDownLatch restarted = new CountDownLatch(3);
            executor.scheduleWithFixedDelay(restarted::countDown, 0, 10, TimeUnit.MILLISECONDS);
            assertTrue("the script has to be startable again after it stopped itself",
                    restarted.await(5, TimeUnit.SECONDS));
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test public void shutdownInterruptsTheRunningTask() throws Exception
    {
        Fixture script = new Fixture();
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) script.scheduledExecutorService;
        try
        {
            CountDownLatch running = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean();
            // A script spends most of its time inside one call - a web walk can run for minutes.
            // Stopping it has to end that call right away, not after it finished on its own.
            script.mainScheduledFuture = executor.scheduleWithFixedDelay(() ->
            {
                running.countDown();
                try
                {
                    Thread.sleep(60_000L);
                }
                catch (InterruptedException ex)
                {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            }, 0, 10, TimeUnit.MILLISECONDS);

            assertTrue(running.await(5, TimeUnit.SECONDS));
            script.shutdown();

            assertTrue("the running task has to be interrupted when the script is stopped",
                    awaitIdle(executor, 5000));
            assertTrue("the task has to see the interrupt", interrupted.get());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test public void shutdownStopsALoopThatWaits() throws Exception
    {
        // Global.sleep asks the client whether it runs on the client thread.
        Client client = mock(Client.class);
        when(client.isClientThread()).thenReturn(false);
        Field clientField = Microbot.class.getDeclaredField("client");
        clientField.setAccessible(true);
        Object previousClient = clientField.get(null);
        clientField.set(null, client);

        Fixture script = new Fixture();
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) script.scheduledExecutorService;
        try
        {
            AtomicInteger ticks = new AtomicInteger();
            // A script that waits in a loop has to leave that loop when it is stopped, even when
            // the wait itself hides the interruption.
            script.mainScheduledFuture = executor.scheduleWithFixedDelay(() ->
            {
                while (true)
                {
                    ticks.incrementAndGet();
                    Global.sleep(100);
                }
            }, 0, 10, TimeUnit.MILLISECONDS);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (ticks.get() < 2 && System.nanoTime() < deadline)
            {
                Thread.sleep(20);
            }
            assertTrue("the loop has to run before it can be stopped", ticks.get() >= 2);

            script.shutdown();

            assertTrue("the waiting loop has to end when the script is stopped", awaitIdle(executor, 5000));
            int afterStop = ticks.get();
            Thread.sleep(200);
            assertEquals("the loop must not tick any more after the script was stopped",
                    afterStop, ticks.get());
        }
        finally
        {
            executor.shutdownNow();
            clientField.set(null, previousClient);
        }
    }

    private static boolean awaitIdle(ScheduledThreadPoolExecutor executor, long timeoutMillis) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (executor.getActiveCount() > 0 && System.nanoTime() < deadline)
        {
            Thread.sleep(20);
        }
        return executor.getActiveCount() == 0;
    }

    private static final class Fixture extends Script
    {
    }
}
