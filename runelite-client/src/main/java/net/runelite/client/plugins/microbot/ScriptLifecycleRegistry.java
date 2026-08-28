package net.runelite.client.plugins.microbot;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks script instances so a side-loaded plugin can release executor threads before its
 * class loader is discarded.
 */
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
        return unterminatedScripts;
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
