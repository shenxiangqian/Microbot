/*
 * Copyright (c) 2023 Microbot
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.externalplugins;

import com.fasterxml.jackson.databind.annotation.NoClass;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.google.common.io.Files;
import com.google.common.reflect.ClassPath;
import com.google.gson.Gson;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Module;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptLifecycleRegistry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.ui.SplashScreen;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotPluginManager {
    private static final File PLUGIN_DIR = new File(RuneLite.RUNELITE_DIR, "microbot-plugins");
    private static final File RUNTIME_PLUGIN_DIR = new File(RuneLite.CACHE_DIR, "microbot-plugin-runtime");
    private static final long SCRIPT_DISPOSE_TIMEOUT_MILLIS = 2_000L;
    private static final String INSTALLED_VERSION_GROUP = "microbotPluginVersions";
    private static final String INSTALLED_VERSION_KEY_PREFIX = "plugin.";
    private static final String UPDATE_NOTIFICATION_GROUP = "microbotPluginUpdateNotifications";
    private static final String UPDATE_NOTIFICATION_KEY_PREFIX = "plugin.";

    private final OkHttpClient okHttpClient;
    private final MicrobotPluginClient microbotPluginClient;
    private final EventBus eventBus;
    private final ScheduledExecutorService executor;
    private final PluginManager pluginManager;
    private final Gson gson;
    private final ConfigManager configManager;
    private final MicrobotApi microbotApi;

    private final Map<String, SideLoadedPluginDeployment> deployments = new ConcurrentHashMap<>();
    private final Set<String> reloadBlockedInternalNames = ConcurrentHashMap.newKeySet();
    private final Object sideLoadLock = new Object();
    private volatile SideLoadedScriptReference lastStartedSideLoadedScript;

    @Inject
    @Named("safeMode")
    private boolean safeMode;

    private final Map<String, MicrobotPluginManifest> manifestMap = new ConcurrentHashMap<>();

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean sideLoadRefreshInProgress = new AtomicBoolean(false);
    private volatile boolean profileRefreshInProgress = false;
    private static final String PLUGIN_PACKAGE = "net.runelite.client.plugins.microbot";

    @Inject
    private MicrobotPluginManager(
            OkHttpClient okHttpClient,
            MicrobotPluginClient microbotPluginClient,
            EventBus eventBus,
            ScheduledExecutorService executor,
            PluginManager pluginManager,
            Gson gson,
            ConfigManager configManager,
            MicrobotApi microbotApi
    ) {
        this.okHttpClient = okHttpClient;
        this.microbotPluginClient = microbotPluginClient;
        this.eventBus = eventBus;
        this.executor = executor;
        this.pluginManager = pluginManager;
        this.gson = gson;
        this.configManager = configManager;
        this.microbotApi = microbotApi;

        PLUGIN_DIR.mkdirs();
        RUNTIME_PLUGIN_DIR.mkdirs();
        cleanupStaleRuntimeJars();
    }

    /**
     * Initializes the MicrobotPluginManager
     */
    public void init() {
        loadManifest();
    }

    /**
     * Loads the plugin manifest list from the remote server and updates the local manifest map.
     * If the manifest has changed, posts an ExternalPluginsChanged event.
     */
    private void loadManifest() {
        try {
            List<MicrobotPluginManifest> manifests = microbotPluginClient.downloadManifest();
            Map<String, MicrobotPluginManifest> next = new HashMap<>(manifests.size());

            com.google.gson.JsonArray allReleases = null;
            try {
                allReleases = microbotPluginClient.fetchAllReleases();
                log.debug("Fetched {} releases from GitHub", allReleases.size());
            } catch (IOException ex) {
                log.warn("Failed to fetch GitHub releases: {}", ex.getMessage());
                log.debug("Releases fetch error", ex);
            }

            for (MicrobotPluginManifest m : manifests) {
                next.put(m.getInternalName(), m);
                if (allReleases != null) {
                    microbotPluginClient.findFirstAssetCreatedAt(m, allReleases)
                            .ifPresent(m::setAddedAt);

                    try {
                        List<String> versions = microbotPluginClient.parseVersionsFromReleases(m, allReleases);
                        m.setAvailableVersions(versions);
                    } catch (IOException ex) {
                        log.warn("Failed to parse available versions for {}: {}", m.getInternalName(), ex.getMessage());
                        log.debug("Version parse error", ex);
                    }
                }
            }
            boolean changed = !next.keySet().equals(manifestMap.keySet())
                    || next.entrySet().stream().anyMatch(e -> {
                MicrobotPluginManifest cur = manifestMap.get(e.getKey());
                return cur == null || !Objects.equals(cur.getSha256(), e.getValue().getSha256());
            });
            if (changed) {
                manifestMap.clear();
                manifestMap.putAll(next);
                log.info("Loaded {} plugin manifests.", manifestMap.size());
                eventBus.post(new ExternalPluginsChanged());
            } else {
                log.debug("Plugin manifests unchanged ({} entries), skipping event.", manifestMap.size());
            }
        } catch (Exception e) {
            log.error("Failed to fetch plugin manifests", e);
        }
    }

    public Map<String, MicrobotPluginManifest> getManifestMap() {
        return Collections.unmodifiableMap(manifestMap);
    }

    /**
     * Gets the File object for the plugin JAR file corresponding to the given internal name.
     *
     * @param internalName the internal name of the plugin
     * @return the File object representing the plugin JAR
     */
    private File getPluginJarFile(String internalName) {
        return new File(PLUGIN_DIR, internalName + ".jar");
    }

    /**
     * Gets the plugin manifest for a given plugin instance.
     *
     * @param internalName
     * @return
     */
    private byte[] getPluginJarByteArray(String internalName) {
        return new File(PLUGIN_DIR, internalName + ".jar").getPath().getBytes();
    }

    /**
     * Creates an OkHttpClient instance that does not use any proxy settings.
     *
     * @param base
     * @return
     */
    private static OkHttpClient noProxy(OkHttpClient base) {
        return base.newBuilder()
                .proxy(Proxy.NO_PROXY)
                .proxySelector(ProxySelector.of(null))
                .build();
    }

    /**
     * Verifies that the SHA-256 hash of a locally installed plugin matches the
     * authoritative hash from the manifest map.
     * <p>
     * This ensures the integrity of the plugin and detects tampering or corruption.
     *
     * @param internalName the internal name of the plugin to verify (must not be null or empty)
     * @return {@code true} if the plugin exists in both the local and authoritative manifests
     * and the SHA-256 hashes match, {@code false} otherwise
     * @throws IllegalArgumentException if {@code internalName} is null or empty
     */
    private boolean verifyHash(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            throw new IllegalArgumentException("Internal name is null/empty");
        }

        MicrobotPluginManifest authoritativeManifest = manifestMap.get(internalName);
        InstalledPluginVersion storedVersion = lookupInstalledPluginVersion(internalName).orElse(null);

        String localHash = calculateHash(internalName);
        String authoritativeHash = storedVersion != null ? storedVersion.getSha256()
                : authoritativeManifest != null ? authoritativeManifest.getSha256() : null;

        if (Strings.isNullOrEmpty(localHash) || Strings.isNullOrEmpty(authoritativeHash)) {
            return false;
        }

        var result = localHash.equals(authoritativeHash);

        if (!result) {
            log.warn("Hash mismatch for plugin {}: local={}, authoritative={}",
                    internalName, localHash, authoritativeHash);
        }
        return result;
    }

    /**
     * Calculates the SHA-256 hash of the given JAR data.
     *
     * @param interalName
     * @return
     */
    private String calculateHash(String interalName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            File file = getPluginJarFile(interalName);
            try (InputStream inputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error calculating plugin hash", e);
            return "";
        }
    }

    public static File[] createSideloadingFolder() {
        try {
            Files.createParentDirs(PLUGIN_DIR);

            if (!PLUGIN_DIR.exists() && PLUGIN_DIR.mkdir()) {
                log.debug("Directory for sideloading was created successfully.");
            }
        } catch (IOException e) {
            log.trace("Error creating directory for microbot-plugins!", e);
        }

        return PLUGIN_DIR.listFiles();
    }

    /**
     * Loads a single plugin from the sideload folder if not already loaded.
     */
    private SideLoadedPluginDeployment loadSideLoadPlugin(String internalName)
            throws PluginInstantiationException, IOException {
        return loadSideLoadPlugin(internalName, true);
    }

    private SideLoadedPluginDeployment loadSideLoadPlugin(String internalName, boolean allowAlwaysOn)
            throws PluginInstantiationException, IOException {
        File pluginFile = getPluginJarFile(internalName);
        if (!pluginFile.exists()) {
            throw new IOException("Plugin file does not exist: " + pluginFile.getName());
        }
        if (deployments.containsKey(internalName)) {
            return deployments.get(internalName);
        }
        if (reloadBlockedInternalNames.contains(internalName)) {
            throw new PluginInstantiationException(
                    "Plugin cannot be loaded again until the client is restarted: " + internalName);
        }
        Set<String> loadedInternalNames = pluginManager.getPlugins().stream()
                .filter(p -> p.getClass().isAnnotationPresent(PluginDescriptor.class))
                .filter(p -> p.getClass().getAnnotation(PluginDescriptor.class).isExternal())
                .map(p -> p.getClass().getSimpleName())
                .collect(Collectors.toSet());
        if (loadedInternalNames.contains(internalName)) {
            throw new PluginInstantiationException("Plugin is already loaded: " + internalName);
        }

        File runtimeJar = createRuntimeJarCopy(pluginFile);
        PluginJarClassLoader classLoader = null;
        List<Plugin> newPlugins = Collections.emptyList();
        try {
            if ((manifestMap.containsKey(internalName) || lookupInstalledPluginVersion(internalName).isPresent())
                    && !verifyHash(internalName)) {
                log.warn("Plugin hash verification failed for: {}", internalName);
            }
            List<Class<?>> plugins = new ArrayList<>();
            classLoader = new PluginJarClassLoader(runtimeJar, getClass().getClassLoader());
            for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {
                try {
                    Class<?> clazz = classLoader.loadClass(classInfo.getName());
                    PluginDescriptor descriptor = clazz.getAnnotation(PluginDescriptor.class);
                    if (!allowAlwaysOn && descriptor != null && descriptor.alwaysOn()) {
                        throw new PluginInstantiationException(
                                "Side-loaded plugins cannot be always-on: " + clazz.getSimpleName());
                    }
                    plugins.add(clazz);
                } catch (ClassNotFoundException e) {
                    log.trace("Class not found during sideloading: {}", classInfo.getName(), e);
                } catch (NoClassDefFoundError e) {
                    log.trace("Dependency not found during sideloading: {}", classInfo.getName(), e);
                }
            }
            newPlugins = loadPlugins(plugins, null);
            if (newPlugins.isEmpty()) {
                throw new PluginInstantiationException("No compatible plugin found in " + pluginFile.getName());
            }

            SideLoadedPluginDeployment deployment = new SideLoadedPluginDeployment(
                    internalName, pluginFile, runtimeJar, classLoader, newPlugins);
            deployments.put(internalName, deployment);
            return deployment;
        } catch (ThreadDeath e) {
            throw e;
        } catch (PluginInstantiationException | IOException e) {
            discardPlugins(newPlugins);
            closeClassLoader(classLoader, internalName);
            deleteRuntimeJar(runtimeJar);
            throw e;
        } catch (Throwable e) {
            discardPlugins(newPlugins);
            closeClassLoader(classLoader, internalName);
            deleteRuntimeJar(runtimeJar);
            throw new PluginInstantiationException(e);
        }
    }

    public void loadSideLoadPlugins() {
        if (safeMode) {
            log.warn("Safe mode is enabled, skipping loading of sideloaded plugins.");
            return;
        }
        synchronized (sideLoadLock) {
            File[] files = listSideLoadJars();
            for (File file : files) {
                String internalName = getInternalName(file);
                if (deployments.containsKey(internalName)) {
                    continue;
                }
                try {
                    loadSideLoadPlugin(internalName);
                } catch (PluginInstantiationException | IOException exception) {
                    log.warn("Unable to load side-loaded plugin {}: {}", internalName, exception.getMessage());
                }
            }
        }
        eventBus.post(new ExternalPluginsChanged());
    }

    /**
     * Returns launchable external plugins owned by this side-load manager.
     */
    public List<SideLoadedScript> getSideLoadedScripts() {
        return deployments.values().stream()
                .flatMap(deployment -> deployment.getPlugins().stream()
                        .filter(MicrobotPluginManager::isLaunchableSideLoadedScript)
                        .map(plugin -> toSideLoadedScript(deployment, plugin)))
                .sorted(Comparator.comparing(SideLoadedScript::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<SideLoadedScript> getActiveSideLoadedScripts() {
        return getSideLoadedScripts().stream()
                .filter(SideLoadedScript::isActive)
                .collect(Collectors.toList());
    }

    public Optional<SideLoadedScriptReference> getLastStartedSideLoadedScript() {
        return Optional.ofNullable(lastStartedSideLoadedScript);
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        if (!event.isLoaded() || !isLaunchableSideLoadedScript(event.getPlugin())) {
            return;
        }

        deployments.values().stream()
                .filter(deployment -> deployment.getPlugins().stream()
                        .anyMatch(plugin -> plugin == event.getPlugin()))
                .findFirst()
                .ifPresent(deployment -> lastStartedSideLoadedScript = new SideLoadedScriptReference(
                        deployment.getInternalName(),
                        event.getPlugin().getClass().getName()));
    }

    public boolean isManagedSideLoadedPlugin(Plugin plugin) {
        return deployments.values().stream()
                .anyMatch(deployment -> deployment.getPlugins().stream().anyMatch(candidate -> candidate == plugin));
    }

    public CompletableFuture<SideLoadedScriptOperationResult> startSideLoadedScript(
            String internalName,
            String className) {
        return executeSideLoadedScriptOperation(
                () -> startSideLoadedScriptNow(internalName, className), false);
    }

    public CompletableFuture<SideLoadedScriptOperationResult> startSideLoadedScriptByName(String scriptName) {
        return executeSideLoadedScriptOperation(
                () -> startSideLoadedScriptByNameNow(scriptName), false);
    }

    public CompletableFuture<SideLoadedScriptOperationResult> stopActiveSideLoadedScripts() {
        return executeSideLoadedScriptOperation(this::stopActiveSideLoadedScriptsNow, false);
    }

    public CompletableFuture<SideLoadedScriptOperationResult> restartSideLoadedScript(
            String internalName,
            String className) {
        return executeSideLoadedScriptOperation(
                () -> restartSideLoadedScriptNow(internalName, className), true);
    }

    private CompletableFuture<SideLoadedScriptOperationResult> executeSideLoadedScriptOperation(
            Supplier<SideLoadedScriptOperationResult> operation,
            boolean notifyExternalPluginsChanged) {
        CompletableFuture<SideLoadedScriptOperationResult> future = new CompletableFuture<>();
        if (safeMode) {
            future.complete(SideLoadedScriptOperationResult.failed("Safe mode is enabled"));
            return future;
        }
        if (isShuttingDown.get()) {
            future.complete(SideLoadedScriptOperationResult.failed("The client is shutting down"));
            return future;
        }

        try {
            executor.execute(() -> {
                try {
                    SideLoadedScriptOperationResult result;
                    synchronized (sideLoadLock) {
                        result = operation.get();
                    }
                    if (notifyExternalPluginsChanged) {
                        eventBus.post(new ExternalPluginsChanged());
                    }
                    future.complete(result);
                } catch (ThreadDeath e) {
                    throw e;
                } catch (Throwable e) {
                    log.warn("Unable to complete side-loaded script operation", e);
                    future.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private SideLoadedScriptOperationResult startSideLoadedScriptNow(String internalName, String className) {
        SideLoadedPluginDeployment deployment = deployments.get(internalName);
        Plugin plugin = findDeploymentPlugin(deployment, className);
        if (plugin == null || !isLaunchableSideLoadedScript(plugin)) {
            return SideLoadedScriptOperationResult.failed("The selected script is no longer loaded");
        }

        List<SideLoadedScript> activeScripts = getActiveSideLoadedScripts();
        boolean anotherScriptActive = activeScripts.stream()
                .anyMatch(script -> !script.matches(internalName, className));
        if (anotherScriptActive) {
            return SideLoadedScriptOperationResult.failed("Stop the currently running script before starting another one");
        }

        return startPluginInstance(deployment, plugin);
    }

    private SideLoadedScriptOperationResult startSideLoadedScriptByNameNow(String scriptName) {
        String requestedName = Strings.nullToEmpty(scriptName).trim();
        if (requestedName.isEmpty()) {
            return SideLoadedScriptOperationResult.failed("Script name must not be blank");
        }

        List<SideLoadedScript> matches = getSideLoadedScripts().stream()
                .filter(script -> script.matchesName(requestedName))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            return SideLoadedScriptOperationResult.failed(
                    "No external script matching '" + requestedName + "' is loaded");
        }
        if (matches.size() > 1) {
            String matchNames = matches.stream()
                    .map(script -> script.getDisplayName() + " [" + script.getInternalName() + "]")
                    .collect(Collectors.joining(", "));
            return SideLoadedScriptOperationResult.failed(
                    "Multiple external scripts match '" + requestedName + "': " + matchNames);
        }

        SideLoadedScript selected = matches.get(0);
        SideLoadedScriptOperationResult stopResult = stopActiveSideLoadedScriptsNow();
        if (!stopResult.isSuccessful()) {
            return stopResult;
        }
        return startSideLoadedScriptNow(selected.getInternalName(), selected.getClassName());
    }

    private SideLoadedScriptOperationResult stopActiveSideLoadedScriptsNow() {
        List<Plugin> activePlugins = deployments.values().stream()
                .flatMap(deployment -> deployment.getPlugins().stream())
                .filter(MicrobotPluginManager::isLaunchableSideLoadedScript)
                .filter(pluginManager::isPluginActive)
                .collect(Collectors.toList());
        if (activePlugins.isEmpty()) {
            return SideLoadedScriptOperationResult.succeeded("No external script is running", null);
        }

        List<String> errors = new ArrayList<>();
        try {
            runOnEdtAndWait(() -> {
                for (Plugin plugin : activePlugins) {
                    try {
                        pluginManager.setPluginEnabled(plugin, false);
                    } catch (RuntimeException e) {
                        errors.add("Failed to disable " + plugin.getClass().getSimpleName());
                        log.warn("Unable to disable side-loaded script {}", plugin.getClass().getSimpleName(), e);
                    }
                    try {
                        if (pluginManager.isPluginActive(plugin)) {
                            pluginManager.stopPlugin(plugin);
                        }
                    } catch (Exception e) {
                        errors.add("Failed to stop " + plugin.getClass().getSimpleName());
                        log.warn("Unable to stop side-loaded script {}", plugin.getClass().getSimpleName(), e);
                    }
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SideLoadedScriptOperationResult.failed("Interrupted while stopping external scripts");
        } catch (InvocationTargetException e) {
            return SideLoadedScriptOperationResult.failed("Unable to stop external scripts on the UI thread");
        }

        if (!errors.isEmpty()) {
            return SideLoadedScriptOperationResult.failed(String.join("; ", errors));
        }
        return SideLoadedScriptOperationResult.succeeded(
                "Stopped " + activePlugins.size() + " external script(s)", null);
    }

    private SideLoadedScriptOperationResult restartSideLoadedScriptNow(String internalName, String className) {
        SideLoadedPluginDeployment deployment = deployments.get(internalName);
        boolean anotherScriptActive = getActiveSideLoadedScripts().stream()
                .anyMatch(script -> !script.matches(internalName, className));
        if (anotherScriptActive) {
            return SideLoadedScriptOperationResult.failed(
                    "Stop the currently running script before restarting the last script");
        }

        if (deployment != null) {
            List<String> unloadErrors = unloadDeployment(deployment, true);
            if (!unloadErrors.isEmpty()) {
                reloadBlockedInternalNames.add(internalName);
                return SideLoadedScriptOperationResult.failed(String.join("; ", unloadErrors));
            }
        }

        SideLoadedPluginDeployment replacement = null;
        try {
            replacement = loadSideLoadPlugin(internalName, false);
            pluginManager.loadDefaultPluginConfiguration(replacement.getPlugins());
            disablePlugins(replacement.getPlugins());
        } catch (PluginInstantiationException | IOException e) {
            if (replacement != null) {
                List<String> cleanupErrors = unloadDeployment(replacement, true);
                if (!cleanupErrors.isEmpty()) {
                    reloadBlockedInternalNames.add(internalName);
                }
            }
            return SideLoadedScriptOperationResult.failed(messageOrClassName(e));
        }

        Plugin reloadedPlugin = findDeploymentPlugin(replacement, className);
        if (reloadedPlugin == null || !isLaunchableSideLoadedScript(reloadedPlugin)) {
            List<String> cleanupErrors = unloadDeployment(replacement, true);
            if (!cleanupErrors.isEmpty()) {
                reloadBlockedInternalNames.add(internalName);
            }
            return SideLoadedScriptOperationResult.failed("The reloaded JAR no longer contains the selected script");
        }
        return startPluginInstance(replacement, reloadedPlugin);
    }

    private SideLoadedScriptOperationResult startPluginInstance(
            SideLoadedPluginDeployment deployment,
            Plugin plugin) {
        try {
            runOnEdtAndWait(() -> {
                pluginManager.setPluginEnabled(plugin, true);
                try {
                    pluginManager.startPlugin(plugin);
                } catch (PluginInstantiationException e) {
                    pluginManager.setPluginEnabled(plugin, false);
                    throw new IllegalStateException(e);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SideLoadedScriptOperationResult.failed("Interrupted while starting the script");
        } catch (InvocationTargetException e) {
            return SideLoadedScriptOperationResult.failed(messageOrClassName(e.getCause()));
        }

        if (!pluginManager.isPluginActive(plugin)) {
            pluginManager.setPluginEnabled(plugin, false);
            return SideLoadedScriptOperationResult.failed("The script did not enter the running state");
        }
        lastStartedSideLoadedScript = new SideLoadedScriptReference(
                deployment.getInternalName(), plugin.getClass().getName());
        return SideLoadedScriptOperationResult.succeeded(
                "Started " + plugin.getName(), toSideLoadedScript(deployment, plugin));
    }

    private static Plugin findDeploymentPlugin(SideLoadedPluginDeployment deployment, String className) {
        if (deployment == null || Strings.isNullOrEmpty(className)) {
            return null;
        }
        return deployment.getPlugins().stream()
                .filter(plugin -> plugin.getClass().getName().equals(className))
                .findFirst()
                .orElse(null);
    }

    @VisibleForTesting
    static boolean isLaunchableSideLoadedScript(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        return descriptor != null
                && descriptor.isExternal()
                && !descriptor.hidden()
                && !descriptor.alwaysOn()
                && !descriptor.disable();
    }

    private SideLoadedScript toSideLoadedScript(SideLoadedPluginDeployment deployment, Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        return new SideLoadedScript(
                deployment.getInternalName(),
                plugin.getClass().getName(),
                descriptor.name(),
                descriptor.description(),
                descriptor.version(),
                pluginManager.getPluginConfigProxy(plugin) != null,
                pluginManager.isPluginActive(plugin),
                plugin);
    }

    private static String messageOrClassName(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return Strings.isNullOrEmpty(current.getMessage())
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    /**
     * Reloads every JAR in the Microbot side-load directory. Reloaded plugins remain disabled.
     */
    public CompletableFuture<SideLoadRefreshResult> refreshSideLoadedPlugins() {
        CompletableFuture<SideLoadRefreshResult> future = new CompletableFuture<>();
        if (safeMode) {
            future.complete(SideLoadRefreshResult.failed("Safe mode is enabled"));
            return future;
        }
        if (isShuttingDown.get()) {
            future.complete(SideLoadRefreshResult.failed("The client is shutting down"));
            return future;
        }
        if (!sideLoadRefreshInProgress.compareAndSet(false, true)) {
            future.complete(SideLoadRefreshResult.failed("A script refresh is already running"));
            return future;
        }

        Runnable refreshTask = () -> {
            try {
                SideLoadRefreshResult result;
                synchronized (sideLoadLock) {
                    result = refreshSideLoadedPluginsNow();
                }
                eventBus.post(new ExternalPluginsChanged());
                future.complete(result);
            } catch (ThreadDeath e) {
                throw e;
            } catch (Throwable e) {
                log.warn("Unable to refresh side-loaded plugins", e);
                try {
                    eventBus.post(new ExternalPluginsChanged());
                } catch (RuntimeException eventError) {
                    log.debug("Unable to notify the UI after script refresh failure", eventError);
                }
                future.completeExceptionally(e);
            } finally {
                sideLoadRefreshInProgress.set(false);
            }
        };
        try {
            executor.execute(refreshTask);
        } catch (RuntimeException e) {
            sideLoadRefreshInProgress.set(false);
            future.completeExceptionally(e);
        }
        return future;
    }

    private SideLoadRefreshResult refreshSideLoadedPluginsNow() {
        SideLoadRefreshResult result = new SideLoadRefreshResult();
        Set<String> failedUnloads = new HashSet<>();
        List<SideLoadedPluginDeployment> currentDeployments = new ArrayList<>(deployments.values());

        for (SideLoadedPluginDeployment deployment : currentDeployments) {
            List<String> unloadErrors = unloadDeployment(deployment, true);
            result.unloaded++;
            if (!unloadErrors.isEmpty()) {
                failedUnloads.add(deployment.getInternalName());
                reloadBlockedInternalNames.add(deployment.getInternalName());
                result.failures.put(deployment.getInternalName(), String.join("; ", unloadErrors));
            }
        }

        for (File file : listSideLoadJars()) {
            String internalName = getInternalName(file);
            if (failedUnloads.contains(internalName) || reloadBlockedInternalNames.contains(internalName)) {
                result.failures.putIfAbsent(internalName, "Restart required before this plugin can be loaded again");
                continue;
            }

            SideLoadedPluginDeployment deployment = null;
            try {
                deployment = loadSideLoadPlugin(internalName, false);
                pluginManager.loadDefaultPluginConfiguration(deployment.getPlugins());
                disablePlugins(deployment.getPlugins());
                result.loaded++;
            } catch (PluginInstantiationException | IOException e) {
                if (deployment != null) {
                    List<String> cleanupErrors = unloadDeployment(deployment, true);
                    if (!cleanupErrors.isEmpty()) {
                        reloadBlockedInternalNames.add(internalName);
                        result.failures.put(
                                internalName,
                                e.getMessage() + "; cleanup failed: " + String.join("; ", cleanupErrors));
                        continue;
                    }
                }
                result.failures.put(internalName, e.getMessage());
                log.warn("Unable to reload side-loaded plugin {}: {}", internalName, e.getMessage());
            }
        }
        return result;
    }

    private File[] listSideLoadJars() {
        File[] files = createSideloadingFolder();
        if (files == null) {
            return new File[0];
        }
        return Arrays.stream(files)
                .filter(File::isFile)
                .filter(file -> file.getName().endsWith(".jar"))
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .toArray(File[]::new);
    }

    private static String getInternalName(File pluginFile) {
        String name = pluginFile.getName();
        return name.substring(0, name.length() - ".jar".length());
    }

    private File createRuntimeJarCopy(File sourceJar) throws IOException {
        java.nio.file.Files.createDirectories(RUNTIME_PLUGIN_DIR.toPath());
        java.nio.file.Path runtimeJar = java.nio.file.Files.createTempFile(
                RUNTIME_PLUGIN_DIR.toPath(), "microbot-plugin-", ".jar");
        try {
            java.nio.file.Files.copy(
                    sourceJar.toPath(), runtimeJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return runtimeJar.toFile();
        } catch (IOException e) {
            java.nio.file.Files.deleteIfExists(runtimeJar);
            throw e;
        }
    }

    private List<String> unloadDeployment(SideLoadedPluginDeployment deployment, boolean disableConfiguration) {
        List<String> errors = new ArrayList<>();
        Runnable stopPlugins = () -> {
            for (Plugin plugin : deployment.getPlugins()) {
                if (disableConfiguration) {
                    try {
                        pluginManager.setPluginEnabled(plugin, false);
                    } catch (RuntimeException e) {
                        errors.add("Failed to disable " + plugin.getClass().getSimpleName());
                        log.warn("Unable to disable side-loaded plugin {}", plugin.getClass().getSimpleName(), e);
                    }
                }
                try {
                    if (pluginManager.isPluginActive(plugin)) {
                        pluginManager.stopPlugin(plugin);
                    }
                } catch (Exception e) {
                    errors.add("Failed to stop " + plugin.getClass().getSimpleName());
                    log.warn("Unable to stop side-loaded plugin {}", plugin.getClass().getSimpleName(), e);
                }
            }
        };

        try {
            runOnEdtAndWait(stopPlugins);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("Interrupted while stopping plugins");
        } catch (InvocationTargetException e) {
            errors.add("Failed to stop plugins on the UI thread");
            log.warn("Unable to stop deployment {}", deployment.getInternalName(), e.getCause());
        }

        List<String> unterminatedScripts = ScriptLifecycleRegistry.disposeScripts(
                deployment.getClassLoader(), SCRIPT_DISPOSE_TIMEOUT_MILLIS);
        if (!unterminatedScripts.isEmpty()) {
            errors.add("Script threads did not stop: " + String.join(", ", unterminatedScripts));
        }

        deployment.getPlugins().forEach(pluginManager::remove);
        deployments.remove(deployment.getInternalName(), deployment);
        if (!closeClassLoader(deployment.getClassLoader(), deployment.getInternalName())) {
            errors.add("Failed to close plugin class loader");
        }
        deleteRuntimeJar(deployment.getRuntimeJar());
        return errors;
    }

    private void disablePlugins(List<Plugin> plugins) throws PluginInstantiationException {
        try {
            runOnEdtAndWait(() -> plugins.forEach(plugin -> pluginManager.setPluginEnabled(plugin, false)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PluginInstantiationException("Interrupted while disabling reloaded plugins");
        } catch (InvocationTargetException e) {
            throw new PluginInstantiationException(e.getCause());
        }
    }

    private void discardPlugins(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            pluginManager.remove(plugin);
        }
    }

    private static void runOnEdtAndWait(Runnable runnable)
            throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeAndWait(runnable);
        }
    }

    private boolean closeClassLoader(PluginJarClassLoader classLoader, String internalName) {
        if (classLoader == null) {
            return true;
        }
        try {
            classLoader.close();
            return true;
        } catch (IOException e) {
            log.warn("Unable to close class loader for {}", internalName, e);
            return false;
        }
    }

    private void deleteRuntimeJar(File runtimeJar) {
        if (runtimeJar == null || !runtimeJar.exists()) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(runtimeJar.toPath());
        } catch (IOException e) {
            log.debug("Unable to delete runtime plugin copy {}", runtimeJar.getName(), e);
        }
    }

    private void cleanupStaleRuntimeJars() {
        File[] staleRuntimeJars = RUNTIME_PLUGIN_DIR.listFiles(file ->
                file.isFile()
                        && file.getName().endsWith(".jar")
                        && file.lastModified() < Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli());
        if (staleRuntimeJars == null) {
            return;
        }
        for (File staleRuntimeJar : staleRuntimeJars) {
            deleteRuntimeJar(staleRuntimeJar);
        }
    }

    /**
     * Topologically sort a graph. Uses Kahn's algorithm.
     *
     * @param graph - A directed graph
     * @param <T>   - The type of the item contained in the nodes of the graph
     * @return - A topologically sorted list corresponding to graph.
     * <p>
     * Multiple invocations with the same arguments may return lists that are not equal.
     */
    @VisibleForTesting
    static <T> List<T> topologicalSort(Graph<T> graph) {
        MutableGraph<T> graphCopy = Graphs.copyOf(graph);
        List<T> l = new ArrayList<>();
        Set<T> s = graphCopy.nodes().stream()
                .filter(node -> graphCopy.inDegree(node) == 0)
                .collect(Collectors.toSet());
        while (!s.isEmpty()) {
            Iterator<T> it = s.iterator();
            T n = it.next();
            it.remove();

            l.add(n);

            for (T m : new HashSet<>(graphCopy.successors(n))) {
                graphCopy.removeEdge(n, m);
                if (graphCopy.inDegree(m) == 0) {
                    s.add(m);
                }
            }
        }
        if (!graphCopy.edges().isEmpty()) {
            throw new RuntimeException("Graph has at least one cycle");
        }
        return l;
    }

    private List<Plugin> loadPlugins(List<Class<?>> plugins, BiConsumer<Integer, Integer> onPluginLoaded) throws PluginInstantiationException {
        MutableGraph<Class<? extends Plugin>> graph = GraphBuilder
                .directed()
                .build();

        Set<Class<?>> alreadyLoaded = pluginManager.getPlugins().stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        for (Class<?> clazz : plugins) {
            if (alreadyLoaded.contains(clazz)) {
                log.debug("Plugin {} is already loaded, skipping duplicate.", clazz.getSimpleName());
                continue;
            }
            PluginDescriptor pluginDescriptor = clazz.getAnnotation(PluginDescriptor.class);

            if (pluginDescriptor == null) {
                if (clazz.getSuperclass() == Plugin.class) {
                    log.error("Class {} is a plugin, but has no plugin descriptor", clazz);
                }
                continue;
            }

            if (clazz.getSuperclass() != Plugin.class) {
                log.error("Class {} has plugin descriptor, but is not a plugin", clazz);
                continue;
            }

            if (pluginDescriptor.isExternal() && !Rs2UiHelper.isClientVersionCompatible(pluginDescriptor.minClientVersion())) {
                log.error("Plugin {} requires client version {} or higher, but current version is {}. Skipping plugin loading.",
                        clazz.getSimpleName(), pluginDescriptor.minClientVersion(), RuneLiteProperties.getMicrobotVersion());
                continue;
            }

            if (pluginDescriptor.disable()) {
                log.error("Plugin {} has been disabled upstream", clazz.getSimpleName());
                continue;
            }

            graph.addNode((Class<Plugin>) clazz);
        }

        for (Class<? extends Plugin> pluginClazz : graph.nodes()) {
            PluginDependency[] pluginDependencies = pluginClazz.getAnnotationsByType(PluginDependency.class);

            for (PluginDependency pluginDependency : pluginDependencies) {
                if (graph.nodes().contains(pluginDependency.value())) {
                    graph.putEdge(pluginDependency.value(), pluginClazz);
                }
            }
        }

        if (Graphs.hasCycle(graph)) {
            throw new PluginInstantiationException("Plugin dependency graph contains a cycle!");
        }

        List<Class<? extends Plugin>> sortedPlugins = topologicalSort(graph);

        int loaded = 0;
        List<Plugin> newPlugins = new ArrayList<>();
        for (Class<? extends Plugin> pluginClazz : sortedPlugins) {
            Plugin plugin;
            try {
                plugin = instantiate(pluginManager.getPlugins(), (Class<Plugin>) pluginClazz);
                log.info("Plugin loaded {}", plugin.getClass().getSimpleName());
                newPlugins.add(plugin);
                pluginManager.addPlugin(plugin);
                loaded++;
            } catch (PluginInstantiationException ex) {
                log.error("Error instantiating plugin!", ex);
            }

            if (onPluginLoaded != null) {
                onPluginLoaded.accept(loaded, sortedPlugins.size());
            }
        }

        return newPlugins;
    }

    private Plugin instantiate(Collection<Plugin> scannedPlugins, Class<Plugin> clazz) throws PluginInstantiationException {
        PluginDependency[] pluginDependencies = clazz.getAnnotationsByType(PluginDependency.class);
        List<Plugin> deps = new ArrayList<>();
        for (PluginDependency pluginDependency : pluginDependencies) {
            Optional<Plugin> dependency = scannedPlugins.stream().filter(p -> p.getClass() == pluginDependency.value()).findFirst();
            if (!dependency.isPresent()) {
                throw new PluginInstantiationException("Unmet dependency for " + clazz.getSimpleName() + ": " + pluginDependency.value().getSimpleName());
            }
            deps.add(dependency.get());
        }

        Plugin plugin;
        try {
            plugin = clazz.getDeclaredConstructor().newInstance();
        } catch (ThreadDeath e) {
            throw e;
        } catch (Throwable ex) {
            throw new PluginInstantiationException(ex);
        }

        try {
            Injector parent = Microbot.getInjector();

            if (deps.size() > 1) {
                List<com.google.inject.Module> modules = new ArrayList<>(deps.size());
                for (Plugin p : deps) {
                    com.google.inject.Module module = (Binder binder) ->
                    {
                        binder.bind((Class<Plugin>) p.getClass()).toInstance(p);
                        binder.install(p);
                    };
                    modules.add(module);
                }

                parent = parent.createChildInjector(modules);
            } else if (!deps.isEmpty()) {
                parent = deps.get(0).getInjector();
            }

            Module pluginModule = (Binder binder) ->
            {
                binder.bind(clazz).toInstance(plugin);
                binder.install(plugin);
            };
            Injector pluginInjector = parent.createChildInjector(pluginModule);
            System.out.println(pluginInjector.getClass().getSimpleName());
            plugin.setInjector(pluginInjector);
        } catch (com.google.common.util.concurrent.ExecutionError e) {
            // Guice/Guava wraps NoClassDefFoundError here
            Throwable cause = e.getCause();
            if (cause instanceof NoClassDefFoundError) {
                log.error("Missing class while loading plugin {}: {}", clazz.getSimpleName(), cause.toString());
            } else {
                log.error("Error while loading plugin {}: {}", clazz.getSimpleName(), e.toString(), e);
            }

            throw new PluginInstantiationException(e);
        } catch (Exception ex) {
            log.error("Incompatible plugin found: {}", clazz.getSimpleName(), ex);
            throw new PluginInstantiationException(ex);
        }

        log.debug("Loaded plugin {}", clazz.getSimpleName());
        return plugin;
    }

    /**
     * Determines if a class is a Microbot-related Plugin that should be loaded.
     * This includes plugins from utility packages, UI components, and specific Microbot systems.
     *
     * @param clazz the class to check
     * @return true if the class should be included in Microbot plugin loading
     */
    private static boolean isMicrobotRelatedPlugin(Class<?> clazz) {
        if (clazz == null || clazz.getPackage() == null) {
            return false;
        }

        if (!Plugin.class.isAssignableFrom(clazz) || clazz == Plugin.class) {
            return false;
        }

        PluginDescriptor descriptor = clazz.getAnnotation(PluginDescriptor.class);
        if (descriptor == null) {
            return false;
        }

        String pkg = clazz.getPackage().getName();

        if (pkg.startsWith(PLUGIN_PACKAGE)) {
            return pkg.equals(PLUGIN_PACKAGE)
                    || pkg.contains(".ui")
                    || pkg.contains(".util")
                    || pkg.contains(".shortestpath")
                    || pkg.contains(".questhelper")
                    || pkg.contains("inventorysetups")
                    || pkg.contains("breakhandler");
        }

        return false;
    }

    /**
     * Scans the classpath for Microbot-related Plugin classes and returns them.
     * This includes plugin classes from utility packages, UI components, and specific Microbot systems.
     *
     * @return list of Microbot-related Plugin classes found on the classpath
     */
    private List<Class<?>> scanForMicrobotPlugins() {
        List<Class<?>> microbotPlugins = new ArrayList<>();

        try {
            ClassPath classPath = ClassPath.from(getClass().getClassLoader());

            for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
                if (!classInfo.getPackageName().startsWith(PLUGIN_PACKAGE)) {
                    continue;
                }

                try {
                    Class<?> clazz = classInfo.load();
                    if (isMicrobotRelatedPlugin(clazz)) {
                        microbotPlugins.add(clazz);
                        log.debug("Found Microbot plugin class: {}", clazz.getName());
                    }
                } catch (Throwable e) {
                    log.trace("Could not load class during Microbot scan: {}", classInfo.getName(), e);
                }
            }

            log.info("Found {} additional Microbot plugin classes during classpath scan", microbotPlugins.size());
        } catch (IOException e) {
            log.error("Failed to scan classpath for Microbot plugin classes", e);
        }

        return microbotPlugins;
    }

    public void loadCorePlugins(List<Class<?>> plugins) throws PluginInstantiationException {
        SplashScreen.stage(.59, null, "Loading plugins");
        List<Class<?>> combinedPlugins = new ArrayList<>(plugins);

        List<Class<?>> additionalMicrobotPlugins = scanForMicrobotPlugins();

        Set<Class<?>> existingPlugins = new HashSet<>(plugins);

        List<Class<?>> newMicrobotPlugins = additionalMicrobotPlugins.stream()
                .filter(clazz -> !existingPlugins.contains(clazz))
                .collect(Collectors.toList());

        combinedPlugins.addAll(newMicrobotPlugins);

        log.info("Loading core plugins: {} passed in + {} core Microbot plugins = {} total",
                plugins.size(), newMicrobotPlugins.size(), combinedPlugins.size());

        if (!combinedPlugins.isEmpty()) {
            loadPlugins(combinedPlugins, (loaded, total) ->
                    SplashScreen.stage(.60, .70, null, "Loading Microbot plugins", loaded, total, false));
        }
    }

    @Subscribe
    public void onClientShutdown(ClientShutdown shutdown) {
        log.info("Client shutdown detected, stopping all Microbot plugins");
        shutdown();
    }

    /**
     * Refreshes plugins
     */
    private void refresh() {
        if (safeMode) {
            log.warn("Safe mode is enabled, skipping loading of sideloaded plugins.");
            return;
        }

        if (isShuttingDown.get()) {
            return;
        }

        synchronized (this) {
            if (profileRefreshInProgress) {
                return;
            }
            profileRefreshInProgress = true;
        }

        try {
            log.debug("Starting plugin refresh");

            List<Plugin> installedPlugins = getInstalledPlugins();

            List<Plugin> disabledPlugins = installedPlugins.stream()
                    .filter(plugin -> {
                        MicrobotPluginManifest upstreamManifest = manifestMap.get(plugin.getClass().getSimpleName());
                        return upstreamManifest != null && upstreamManifest.isDisable();
                    })
                    .collect(Collectors.toList());

            if (!disabledPlugins.isEmpty()) {
                log.warn("Found {} disabled plugin(s) that have been disabled upstream:", disabledPlugins.size());
                for (Plugin disabledPlugin : disabledPlugins) {
                    log.warn("  - Plugin '{}' has been disabled upstream and will be removed from your installed plugins",
                            disabledPlugin.getClass().getSimpleName());
                }

                List<Plugin> enabledPlugins = installedPlugins.stream()
                        .filter(plugin -> {
                            MicrobotPluginManifest upstreamManifest = manifestMap.get(plugin.getClass().getSimpleName());
                            return upstreamManifest == null || !upstreamManifest.isDisable();
                        })
                        .collect(Collectors.toList());

                installedPlugins = enabledPlugins;

                log.info("Automatically removed {} disabled plugin(s) from your installed plugins list", disabledPlugins.size());
            }

            Set<String> installedNames = installedPlugins.stream()
                    .map(plugin -> plugin.getClass().getSimpleName())
                    .collect(Collectors.toSet());

            List<Plugin> allLoadedPlugins = new ArrayList<>(pluginManager.getPlugins());

            List<Plugin> loadedExternalPlugins = allLoadedPlugins.stream()
                    .filter(plugin -> {
                        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                        if (descriptor == null || !descriptor.isExternal()) {
                            return false;
                        }
                        String packageName = plugin.getClass().getPackage().getName();
                        return packageName.contains("microbot");
                    })
                    .collect(Collectors.toList());

            Set<String> loadedByInternalName = loadedExternalPlugins.stream()
                    .map(this::getPluginManifest) // your helper
                    .filter(Objects::nonNull)
                    .map(MicrobotPluginManifest::getInternalName)
                    .collect(Collectors.toSet());

            log.info("Profile refresh - Installed plugins: {}, Currently loaded Microbot plugins: {}",
                    installedNames, loadedByInternalName);

            log.debug("All loaded plugins ({}):", allLoadedPlugins.size());
            for (Plugin plugin : allLoadedPlugins) {
                PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                boolean isExternal = descriptor != null && descriptor.isExternal();
                MicrobotPluginManifest manifest = getPluginManifest(plugin);
                log.debug("  - {} (external: {}, has manifest: {})",
                        plugin.getClass().getSimpleName(), isExternal, manifest != null);
            }

            // TODO: current code will redownload all plugins, because manifestMap is a manifest of all the plugins on the hub
            // we need to filter it down to only the ones the user has installed
            // what i think we need to do:
            // create a filtered down list of manifestmap that contains only  the installed plugins
            // add your "install" plugin to this list, then use this list here
            var userManifestMap = manifestMap.entrySet().stream()
                    .filter(e -> installedNames.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


            Set<String> needsDownload = userManifestMap.keySet().stream()
                    .filter(microbotPluginManifest -> !getPluginJarFile(microbotPluginManifest).exists())
                    .collect(Collectors.toSet());

            Set<String> needsRedownload = userManifestMap.keySet().stream()
                    .filter(pluginName -> {
                        File pluginFile = getPluginJarFile(pluginName);
                        if (!pluginFile.exists()) {
                            return false;
                        }
                        if (!verifyHash(pluginName)) {
                            log.info("Hash verification failed for plugin: {}. Marking for redownload.", pluginName);
                            if (pluginFile.delete()) {
                                log.info("Deleted outdated plugin file: {}", pluginFile.getClass().getSimpleName());
                            } else {
                                log.warn("Failed to delete outdated plugin file: {}", pluginFile.getAbsolutePath());
                            }
                            return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toSet());

            needsDownload.addAll(needsRedownload);

            Set<String> needsReload = new HashSet<>(needsDownload);

            Set<File> keepFiles = userManifestMap.keySet().stream()
                    .map(this::getPluginJarFile)
                    .filter(File::exists)
                    .collect(Collectors.toSet());

            Instant now = Instant.now();
            Instant keepAfter = now.minus(3, ChronoUnit.DAYS);

            Optional.ofNullable(PLUGIN_DIR.listFiles((dir, name) -> name.endsWith(".jar"))).stream()
                    .flatMap(Arrays::stream)
                    .filter(file -> !keepFiles.contains(file) && file.lastModified() < keepAfter.toEpochMilli())
                    .forEach(file -> {
                        log.info("Cleaning up old plugin file (>3 days): {}", file.getName());
                        if (!file.delete()) {
                            log.warn("Failed to delete old plugin file: {}", file.getAbsolutePath());
                        }
                    });

            for (String pluginName : needsDownload) {
                log.info("Downloading missing plugin: {}", pluginName);
                String desiredVersion = getInstalledPluginVersion(pluginName)
                        .orElse(null);
                downloadPlugin(pluginName, desiredVersion);
            }

            Set<String> installedPluginNames = userManifestMap.values().stream()
                    .map(MicrobotPluginManifest::getInternalName)
                    .collect(Collectors.toSet());

            Set<MicrobotPluginManifest> toAdd = userManifestMap.values().stream()
                    .filter(m -> needsReload.contains(m.getInternalName())
                            || !loadedByInternalName.contains(m.getInternalName()))
                    .collect(Collectors.toSet());

            List<Plugin> toRemove = loadedExternalPlugins.stream()
                    .filter(p -> {
                        MicrobotPluginManifest m = getPluginManifest(p);
                        if (m == null) return true; // unknown → remove
                        String name = m.getInternalName();
                        return !installedPluginNames.contains(name) || needsReload.contains(name);
                    })
                    .collect(Collectors.toList());

            log.info("Plugin refresh - Will add: {} plugins, Will remove: {} plugins",
                    toAdd.stream().map(MicrobotPluginManifest::getInternalName).collect(Collectors.toSet()),
                    toRemove.stream().map(p -> p.getClass().getSimpleName()).collect(Collectors.toSet()));

            for (Plugin plugin : toRemove) {
                String simple = plugin.getClass().getSimpleName();
                log.info("Stopping plugin \"{}\"", simple);
                try {
                    SwingUtilities.invokeAndWait(() -> stopPlugin(plugin));
                } catch (InterruptedException | InvocationTargetException e) {
                    log.warn("Failed to stop plugin {}", simple, e);
                }
            }


            for (MicrobotPluginManifest manifest : toAdd) {
                String pluginName = manifest.getInternalName();
                File pluginFile = getPluginJarFile(pluginName);
                if (!pluginFile.exists()) {
                    log.warn("Plugin file missing for {}, skipping load", pluginName);
                    continue;
                }

                log.info("Loading plugin \"{}\"", pluginName);
                List<Plugin> newPlugins = null;
                PluginJarClassLoader classLoader = null;
                try {
                    if (!verifyHash(pluginName)) {
                        log.warn("Plugin hash verification failed for: {}. The installed version may be outdated or from a different source.", pluginName);
                        continue;
                    }

                    List<Class<?>> pluginClasses = new ArrayList<>();
                    classLoader = new PluginJarClassLoader(pluginFile, getClass().getClassLoader());

                    for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {
                        try {
                            Class<?> clazz = classLoader.loadClass(classInfo.getName());
                            pluginClasses.add(clazz);
                        } catch (ClassNotFoundException e) {
                            log.trace("Class not found during plugin loading: {}", classInfo.getName(), e);
                        } catch(Throwable t) {
                            log.error("Incompatible plugin found: " + pluginName);
                        }
                    }

                    newPlugins = loadPlugins(pluginClasses, null);

                    boolean startup = SplashScreen.isOpen();
                    if (!startup && !newPlugins.isEmpty()) {
                        pluginManager.loadDefaultPluginConfiguration(newPlugins);
                        final List<Plugin> pluginsToStart = newPlugins;
                        SwingUtilities.invokeAndWait(() -> {
                            try {
                                for (Plugin p : pluginsToStart) {
                                    pluginManager.startPlugin(p);
                                }
                            } catch (PluginInstantiationException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    log.info("Successfully loaded plugin: {}", pluginName);
                } catch (ThreadDeath e) {
                    throw e;
                } catch (Throwable e) {
                    log.warn("Unable to load or start plugin \"{}\"", pluginName, e);
                }
            }

            if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
                eventBus.post(new ExternalPluginsChanged());
            }
            log.info("Completed plugin refresh - Added: {}, Removed: {}", toAdd.size(), toRemove.size());
        } catch (Exception e) {
            log.error("Error during plugin refresh", e);
        } finally {
            profileRefreshInProgress = false;
        }
    }

    /**
     * Downloads a plugin JAR file from the remote server.
     *
     * @param internalName the internal name of the plugin to download
     * @return true if the plugin was successfully downloaded, false otherwise
     */
    private boolean downloadPlugin(String internalName, @Nullable String versionOverride) {
        MicrobotPluginManifest manifest = manifestMap.get(internalName);
        if (manifest == null) {
            log.error("Cannot download plugin {}: manifest not found", internalName);
            return false;
        }

        try {
            File pluginFile = getPluginJarFile(internalName);

            String versionToDownload = !Strings.isNullOrEmpty(versionOverride) ? versionOverride : manifest.getVersion();
            if (Strings.isNullOrEmpty(versionToDownload)) {
                log.error("Cannot determine version to download for {}", internalName);
                return false;
            }

            HttpUrl jarUrl = microbotPluginClient.getJarURL(manifest, versionToDownload);
            if (jarUrl == null || !jarUrl.isHttps()) {
                log.error("Invalid JAR URL for plugin {}", internalName);
                return false;
            }

            OkHttpClient clientWithoutProxy = noProxy(okHttpClient);
            Request request = new Request.Builder()
                    .url(jarUrl)
                    .build();

            log.info("from url : " + jarUrl);

            try (Response response = clientWithoutProxy.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Failed to download plugin {}: HTTP {}", internalName, response.code());
                    return false;
                }

                byte[] jarData = response.body().bytes();

                Files.write(jarData, pluginFile);
                log.info("Plugin {} (version {}) downloaded to {}", internalName, versionToDownload, pluginFile.getAbsolutePath());

                String authoritativeHash = versionToDownload.equals(manifest.getVersion()) ? manifest.getSha256() : null;
                if (Strings.isNullOrEmpty(authoritativeHash)) {
                    authoritativeHash = calculateHash(internalName);
                }
                if (!Strings.isNullOrEmpty(authoritativeHash)) {
                    rememberInstalledPluginVersion(internalName, versionToDownload, authoritativeHash);
                }

                return true;
            }

        } catch (Exception e) {
            log.error("Failed to download plugin {}", internalName, e);

            File pluginFile = getPluginJarFile(internalName);
            if (pluginFile.exists() && !pluginFile.delete()) {
                log.warn("Failed to delete corrupted plugin file: {}", pluginFile.getAbsolutePath());
            }
            return false;
        }
    }

    /**
     * Installs a plugin and triggers UI refresh.
     *
     * @param manifest the manifest of the plugin to install
     */
    public void installPlugin(MicrobotPluginManifest manifest, @Nullable String versionOverride) {
        executor.submit(() -> install(manifest, versionOverride));
    }

    /**
     * Removes a plugin and triggers UI refresh.
     *
     * @param manifest the manifest of the plugin to remove
     */
    public void removePlugin(MicrobotPluginManifest manifest) {
        executor.submit(() -> remove(manifest));
    }

    /**
     * Installs a plugin by adding it to the installed plugins list in config.
     *
     * @param manifest the manifest of the plugin to install
     */
    public void install(MicrobotPluginManifest manifest, @Nullable String versionOverride) {
        if (manifest == null || !manifestMap.containsValue(manifest)) {
            log.error("Can't install plugin: unable to identify manifest");
            return;
        }

        final String internalName = manifest.getInternalName();
        if (internalName == null || internalName.isEmpty()) {
            log.error("Cannot install plugin: internal name is null or empty");
            return;
        }

        if (manifest.isDisable()) {
            log.warn("Cannot install plugin '{}' ({}): This plugin has been disabled upstream by the developers. " +
                            "This usually means the plugin is no longer functional, has security issues, or has been deprecated.",
                    manifest.getDisplayName(), internalName);
            return;
        }

        synchronized (sideLoadLock) {
            boolean downloaded = downloadPlugin(internalName, versionOverride);
            if (downloaded) {
                try {
                    loadSideLoadPlugin(internalName);
                    sendPluginInstallTelemetry(manifest, versionOverride);
                } catch (PluginInstantiationException | IOException e) {
                    log.warn("Unable to load installed plugin {}: {}", internalName, e.getMessage());
                }
            }
        }

        log.info("Added plugin {} to installed list", manifest.getDisplayName());
        eventBus.post(new ExternalPluginsChanged());
    }

    /**
     * Removes a plugin by removing it from the installed plugins list in config.
     *
     * @param manifest the manifest of the plugin to remove
     */
    public void remove(MicrobotPluginManifest manifest) {
        if (manifest == null || !manifestMap.containsValue(manifest)) {
            log.error("Can't install plugin: unable to identify manifest");
            return;
        }

        final String internalName = manifest.getInternalName();
        if (internalName == null || internalName.isEmpty()) {
            log.error("Cannot install plugin: internal name is null or empty");
            return;
        }

        if (manifest.isDisable()) {
            log.warn("Cannot install plugin '{}' ({}): This plugin has been disabled upstream by the developers. " +
                            "This usually means the plugin is no longer functional, has security issues, or has been deprecated.",
                    manifest.getDisplayName(), internalName);
            return;
        }

        synchronized (sideLoadLock) {
            File jar = getPluginJarFile(internalName);
            SideLoadedPluginDeployment deployment = deployments.get(internalName);
            if (deployment != null) {
                List<String> errors = unloadDeployment(deployment, false);
                if (!errors.isEmpty()) {
                    reloadBlockedInternalNames.add(internalName);
                    log.warn("Plugin {} was removed with cleanup errors: {}", internalName, errors);
                }
            } else {
                log.warn("Plugin deployment to remove not found: {}", internalName);
            }

            if (jar.exists() && !jar.delete()) {
                log.warn("Failed to delete plugin jar {}", jar.getAbsolutePath());
            }
            clearInstalledPluginVersion(internalName);
        }

        log.info("Removed plugin {} from installed list", manifest.getDisplayName());
        eventBus.post(new ExternalPluginsChanged());
    }

    private void sendPluginInstallTelemetry(MicrobotPluginManifest manifest, @Nullable String versionOverride)
    {
        if (manifest == null) {
            return;
        }

        String version = Strings.isNullOrEmpty(versionOverride) ? manifest.getVersion() : versionOverride;
        microbotApi.increasePluginInstall(manifest.getInternalName(), manifest.getDisplayName(), version);
    }

    /**
     * Updates a plugin by redownloading and reloading it.
     *
     * @param manifest
     */
    public void update(MicrobotPluginManifest manifest, @Nullable String versionOverride) {
        // remove and download the new one
        remove(manifest);
        install(manifest, versionOverride);
    }

    /**
     * @return
     * @apiNote
     */
    public List<Plugin> getInstalledPlugins() {
        Predicate<Plugin> isExternalPluginPredicate = plugin ->
                plugin.getClass().getAnnotation(PluginDescriptor.class).isExternal();

        List<Plugin> loadedPlugins = pluginManager.getPlugins()
                .stream()
                .filter(plugin -> !plugin.getClass().getAnnotation(PluginDescriptor.class).hidden())
                .filter(isExternalPluginPredicate)
                .collect(Collectors.toList());

        return loadedPlugins;
    }

    /**
     * Submits a plugin refresh task to the executor.
     * This will reload plugins based on the current profile's installed plugins list.
     */
    public void updatePlugin(MicrobotPluginManifest manifest, @Nullable String versionOverride) {
        executor.submit(() -> {
            update(manifest, versionOverride);
        });
    }

    /**
     * Gets the manifest for a given plugin, this pulls from the global manifest map.
     *
     * @param plugin the plugin to get the manifest for
     * @return the manifest for the plugin, or null if not found or not an external plugin
     */
    @Nullable
    private MicrobotPluginManifest getPluginManifest(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null || !descriptor.isExternal()) {
            return null;
        }

        String internalName = plugin.getClass().getSimpleName();

        return manifestMap.get(internalName);
    }

    /**
     * Gracefully stops a plugin
     */
    private void stopPlugin(Plugin plugin) {
        String pluginName = plugin.getClass().getSimpleName();

        try {
            if (pluginManager.isPluginActive(plugin)) {
                if (SwingUtilities.isEventDispatchThread()) {
                    pluginManager.stopPlugin(plugin);
                    return;
                } else  {
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            pluginManager.stopPlugin(plugin);
                        } catch (PluginInstantiationException e) {
                            log.warn("Error stopping plugin {}: {}", pluginName, e.getMessage());
                        }
                    });
                }
            }
            pluginManager.remove(plugin);
        } catch (Exception e) {
            log.warn("Error during plugin stop for {}: {}", pluginName, e.getMessage());
        }
    }

    /**
     * Gracefully shuts down the plugin manager and performs final cleanup.
     */
    private void shutdown() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            return;
        }

        log.info("Shutting down MicrobotPluginManager");

        try {
            synchronized (sideLoadLock) {
                for (SideLoadedPluginDeployment deployment : new ArrayList<>(deployments.values())) {
                    List<String> errors = unloadDeployment(deployment, false);
                    if (!errors.isEmpty()) {
                        log.warn("Plugin {} shutdown completed with cleanup errors: {}",
                                deployment.getInternalName(), errors);
                    }
                }
            }

            log.info("MicrobotPluginManager shutdown complete");
        } catch (Exception e) {
            log.error("Error during MicrobotPluginManager shutdown", e);
        }
    }

    private Optional<InstalledPluginVersion> lookupInstalledPluginVersion(String internalName) {
        if (Strings.isNullOrEmpty(internalName)) {
            return Optional.empty();
        }
        String key = INSTALLED_VERSION_KEY_PREFIX + internalName;
        String value = configManager.getConfiguration(INSTALLED_VERSION_GROUP, key);
        if (Strings.isNullOrEmpty(value)) {
            return Optional.empty();
        }

        String[] parts = value.split(":", 2);
        if (parts.length < 2 || Strings.isNullOrEmpty(parts[0]) || Strings.isNullOrEmpty(parts[1])) {
            return Optional.empty();
        }

        return Optional.of(new InstalledPluginVersion(parts[0], parts[1]));
    }

    public Optional<String> getInstalledPluginVersion(String internalName) {
        return lookupInstalledPluginVersion(internalName).map(InstalledPluginVersion::getVersion);
    }

    public Optional<OutdatedPluginUpdate> getOutdatedPluginUpdate(Plugin plugin) {
        MicrobotPluginManifest manifest = getPluginManifest(plugin);
        if (manifest == null) {
            return Optional.empty();
        }

        String internalName = manifest.getInternalName();
        String latestVersion = manifest.getVersion();
        String installedVersion = getInstalledPluginVersion(internalName)
                .orElseGet(() -> {
                    PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                    return descriptor == null ? null : descriptor.version();
                });

        if (Strings.isNullOrEmpty(internalName)
                || Strings.isNullOrEmpty(installedVersion)
                || Strings.isNullOrEmpty(latestVersion)
                || latestVersion.equals(installedVersion)) {
            return Optional.empty();
        }

        String notificationKey = UPDATE_NOTIFICATION_KEY_PREFIX + internalName;
        String notifiedVersion = configManager.getConfiguration(UPDATE_NOTIFICATION_GROUP, notificationKey);
        if (latestVersion.equals(notifiedVersion)) {
            return Optional.empty();
        }

        return Optional.of(new OutdatedPluginUpdate(
                internalName,
                Strings.isNullOrEmpty(manifest.getDisplayName()) ? internalName : manifest.getDisplayName(),
                installedVersion,
                latestVersion
        ));
    }

    public void rememberOutdatedPluginUpdateNotification(OutdatedPluginUpdate outdatedPluginUpdate) {
        if (outdatedPluginUpdate == null || Strings.isNullOrEmpty(outdatedPluginUpdate.getInternalName())
                || Strings.isNullOrEmpty(outdatedPluginUpdate.getLatestVersion())) {
            return;
        }

        configManager.setConfiguration(
                UPDATE_NOTIFICATION_GROUP,
                UPDATE_NOTIFICATION_KEY_PREFIX + outdatedPluginUpdate.getInternalName(),
                outdatedPluginUpdate.getLatestVersion()
        );
    }

    private void rememberInstalledPluginVersion(String internalName, String version, String sha256) {
        if (Strings.isNullOrEmpty(internalName) || Strings.isNullOrEmpty(version) || Strings.isNullOrEmpty(sha256)) {
            return;
        }

        configManager.setConfiguration(
                INSTALLED_VERSION_GROUP,
                INSTALLED_VERSION_KEY_PREFIX + internalName,
                version + ":" + sha256
        );
    }

    private void clearInstalledPluginVersion(String internalName) {
        if (Strings.isNullOrEmpty(internalName)) {
            return;
        }
        configManager.unsetConfiguration(INSTALLED_VERSION_GROUP, INSTALLED_VERSION_KEY_PREFIX + internalName);
    }

    private static final class InstalledPluginVersion {
        private final String version;
        private final String sha256;

        private InstalledPluginVersion(String version, String sha256) {
            this.version = version;
            this.sha256 = sha256;
        }

        public String getVersion() {
            return version;
        }

        public String getSha256() {
            return sha256;
        }
    }

    public static final class SideLoadRefreshResult {
        private int unloaded;
        private int loaded;
        private final Map<String, String> failures = new LinkedHashMap<>();

        private static SideLoadRefreshResult failed(String message) {
            SideLoadRefreshResult result = new SideLoadRefreshResult();
            result.failures.put("Refresh", message);
            return result;
        }

        public int getUnloaded() {
            return unloaded;
        }

        public int getLoaded() {
            return loaded;
        }

        public Map<String, String> getFailures() {
            return Collections.unmodifiableMap(failures);
        }

        public boolean isSuccessful() {
            return failures.isEmpty();
        }

        public String getSummary() {
            return String.format(
                    "Reloaded %d script JAR(s), unloaded %d, failed %d",
                    loaded, unloaded, failures.size());
        }
    }

    public static final class SideLoadedScript {
        private final String internalName;
        private final String className;
        private final String displayName;
        private final String description;
        private final String version;
        private final boolean configurable;
        private final boolean active;
        private final Plugin plugin;

        private SideLoadedScript(
                String internalName,
                String className,
                String displayName,
                String description,
                String version,
                boolean configurable,
                boolean active,
                Plugin plugin) {
            this.internalName = internalName;
            this.className = className;
            this.displayName = displayName;
            this.description = description;
            this.version = version;
            this.configurable = configurable;
            this.active = active;
            this.plugin = plugin;
        }

        public String getInternalName() {
            return internalName;
        }

        public String getClassName() {
            return className;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public String getVersion() {
            return version;
        }

        public boolean isConfigurable() {
            return configurable;
        }

        public boolean isActive() {
            return active;
        }

        public Plugin getPlugin() {
            return plugin;
        }

        private boolean matches(String candidateInternalName, String candidateClassName) {
            return internalName.equals(candidateInternalName) && className.equals(candidateClassName);
        }

        private boolean matchesName(String candidateName) {
            return matchesSideLoadedScriptName(candidateName, internalName, className, displayName);
        }
    }

    @VisibleForTesting
    static boolean matchesSideLoadedScriptName(
            String candidateName,
            String internalName,
            String className,
            String displayName) {
        if (Strings.isNullOrEmpty(candidateName)) {
            return false;
        }
        int simpleNameOffset = className.lastIndexOf('.') + 1;
        String simpleClassName = className.substring(simpleNameOffset);
        return candidateName.equalsIgnoreCase(displayName)
                || candidateName.equalsIgnoreCase(internalName)
                || candidateName.equalsIgnoreCase(className)
                || candidateName.equalsIgnoreCase(simpleClassName);
    }

    public static final class SideLoadedScriptOperationResult {
        private final boolean successful;
        private final String message;
        private final SideLoadedScript script;

        private SideLoadedScriptOperationResult(
                boolean successful,
                String message,
                SideLoadedScript script) {
            this.successful = successful;
            this.message = message;
            this.script = script;
        }

        private static SideLoadedScriptOperationResult succeeded(String message, SideLoadedScript script) {
            return new SideLoadedScriptOperationResult(true, message, script);
        }

        private static SideLoadedScriptOperationResult failed(String message) {
            return new SideLoadedScriptOperationResult(false, message, null);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getMessage() {
            return message;
        }

        public SideLoadedScript getScript() {
            return script;
        }
    }

    public static final class SideLoadedScriptReference {
        private final String internalName;
        private final String className;

        private SideLoadedScriptReference(String internalName, String className) {
            this.internalName = internalName;
            this.className = className;
        }

        public String getInternalName() {
            return internalName;
        }

        public String getClassName() {
            return className;
        }
    }

    public static final class OutdatedPluginUpdate {
        private final String internalName;
        private final String displayName;
        private final String installedVersion;
        private final String latestVersion;

        private OutdatedPluginUpdate(String internalName, String displayName, String installedVersion, String latestVersion) {
            this.internalName = internalName;
            this.displayName = displayName;
            this.installedVersion = installedVersion;
            this.latestVersion = latestVersion;
        }

        public String getInternalName() {
            return internalName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getInstalledVersion() {
            return installedVersion;
        }

        public String getLatestVersion() {
            return latestVersion;
        }
    }
}
