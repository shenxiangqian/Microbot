package net.runelite.client.plugins.microbot;

import lombok.extern.slf4j.Slf4j;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks script instances so a side-loaded plugin can release executor threads before its
 * class loader is discarded.
 */
@Slf4j
public final class ScriptLifecycleRegistry {
    private static final Set<WeakReference<Script>> SCRIPTS = ConcurrentHashMap.newKeySet();

    private ScriptLifecycleRegistry() {
    }

    static void register(Script script) {
        removeClearedReferences();
        SCRIPTS.add(new WeakReference<>(script));
    }

    /**
     * Permanently disposes scripts defined by {@code classLoader} within one shared timeout.
     *
     * @return names of scripts whose executors did not terminate in time
     */
    public static List<String> disposeScripts(ClassLoader classLoader, long timeoutMillis) {
        List<Script> matchingScripts = new ArrayList<>();
        for (WeakReference<Script> reference : SCRIPTS) {
            Script script = reference.get();
            if (script == null) {
                SCRIPTS.remove(reference);
            } else if (script.getClass().getClassLoader() == classLoader) {
                matchingScripts.add(script);
            }
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        List<String> unterminatedScripts = new ArrayList<>();
        for (Script script : matchingScripts) {
            long remainingNanos = Math.max(0, deadline - System.nanoTime());
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            if (!script.disposeForClassLoaderUnload(remainingMillis)) {
                unterminatedScripts.add(script.getClass().getSimpleName());
            }
            unregister(script);
        }
        if (!unterminatedScripts.isEmpty()) {
            log.warn("Script threads did not stop: {}{}", String.join(", ", unterminatedScripts),
                    describeSurvivingThreads(unterminatedScripts));
        }
        return unterminatedScripts;
    }

    /**
     * Waits until no registered script has a running task left.
     *
     * <p>Used after the scripts were stopped, so the reported state is the real one: a script that
     * ignores the interrupt is named instead of only being suspected.</p>
     *
     * @param timeoutMillis how long to wait in total
     * @return the names of the scripts that were still busy
     */
    public static List<String> awaitScriptsIdle(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        List<String> busyScripts = new ArrayList<>();
        for (WeakReference<Script> reference : SCRIPTS) {
            Script script = reference.get();
            if (script == null) {
                continue;
            }
            long remainingNanos = Math.max(0, deadline - System.nanoTime());
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            if (!script.awaitIdle(remainingMillis)) {
                busyScripts.add(script.getClass().getSimpleName());
            }
        }
        return busyScripts;
    }

    /**
     * Dumps the threads of the scripts that did not terminate, so a leftover loop that keeps
     * burning CPU can be identified in the log instead of only being suspected.
     *
     * @param scriptNames simple names of the scripts to look for
     * @return a human readable description of the live threads
     */
    public static String describeSurvivingThreads(List<String> scriptNames) {
        StringBuilder description = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            if (!belongsToScript(thread.getName(), scriptNames)) {
                continue;
            }
            description.append("\n  ").append(thread.getName()).append(" [").append(thread.getState()).append(']');
            StackTraceElement[] trace = entry.getValue();
            for (int i = 0; i < Math.min(trace.length, 6); i++) {
                description.append("\n    at ").append(trace[i]);
            }
        }
        if (description.length() == 0) {
            description.append("\n  (no live thread carries a matching script name)");
        }
        return description.toString();
    }

    private static boolean belongsToScript(String threadName, List<String> scriptNames) {
        for (String scriptName : scriptNames) {
            if (threadName.startsWith(scriptName)) {
                return true;
            }
        }
        return false;
    }

    private static void unregister(Script script) {
        SCRIPTS.removeIf(reference -> {
            Script tracked = reference.get();
            return tracked == null || tracked == script;
        });
    }

    private static void removeClearedReferences() {
        SCRIPTS.removeIf(reference -> reference.get() == null);
    }
}
