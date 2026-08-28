package net.runelite.client.plugins.microbot;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScriptLifecycleRegistryTest {
    @Test
    public void normalShutdownKeepsExecutorReusable() throws Exception {
        TestScript script = new TestScript();

        script.startTask();
        assertTrue(script.awaitRun());
        script.shutdown();

        assertFalse(script.isExecutorShutdown());
        script.startTask();
        assertTrue(script.awaitRun());

        assertTrue(script.disposeForClassLoaderUnload(1_000L));
        assertTrue(script.isExecutorShutdown());
    }

    @Test
    public void registryDisposesScriptsForClassLoader() throws Exception {
        TestScript script = new TestScript();
        script.startTask();
        assertTrue(script.awaitRun());

        List<String> failures = ScriptLifecycleRegistry.disposeScripts(
                script.getClass().getClassLoader(), 1_000L);

        assertTrue(failures.toString(), failures.isEmpty());
        assertTrue(script.isExecutorShutdown());
    }

    @Test
    public void registryReportsScriptThatIgnoresInterrupts() throws Exception {
        UncooperativeScript script = new UncooperativeScript();
        script.startTask();
        assertTrue(script.awaitRun());

        List<String> failures = ScriptLifecycleRegistry.disposeScripts(
                script.getClass().getClassLoader(), 20L);

        assertTrue(failures.toString(), failures.contains(UncooperativeScript.class.getSimpleName()));
        script.releaseTask();
        assertTrue(script.awaitExecutorTermination());
    }

    private static final class TestScript extends Script {
        private CountDownLatch ran = new CountDownLatch(1);

        private void startTask() {
            ran = new CountDownLatch(1);
            scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
                    ran::countDown, 0, 10, TimeUnit.MILLISECONDS);
        }

        private boolean awaitRun() throws InterruptedException {
            return ran.await(1, TimeUnit.SECONDS);
        }

        private boolean isExecutorShutdown() {
            return scheduledExecutorService.isShutdown();
        }

        @Override
        public void shutdown() {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
            }
        }
    }

    private static final class UncooperativeScript extends Script {
        private final CountDownLatch ran = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private void startTask() {
            scheduledFuture = scheduledExecutorService.schedule(() -> {
                ran.countDown();
                boolean released = false;
                while (!released) {
                    try {
                        release.await();
                        released = true;
                    } catch (InterruptedException ignored) {
                        // Deliberately ignore interruption to verify unload failure handling.
                    }
                }
            }, 0, TimeUnit.MILLISECONDS);
        }

        private boolean awaitRun() throws InterruptedException {
            return ran.await(1, TimeUnit.SECONDS);
        }

        private void releaseTask() {
            release.countDown();
        }

        private boolean awaitExecutorTermination() throws InterruptedException {
            return scheduledExecutorService.awaitTermination(1, TimeUnit.SECONDS);
        }

        @Override
        public void shutdown() {
            // The worker intentionally remains active until releaseTask() is called.
        }
    }
}
