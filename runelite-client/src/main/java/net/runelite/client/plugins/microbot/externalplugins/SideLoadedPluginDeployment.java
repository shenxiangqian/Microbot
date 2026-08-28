package net.runelite.client.plugins.microbot.externalplugins;

import net.runelite.client.plugins.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SideLoadedPluginDeployment {
    private final String internalName;
    private final File sourceJar;
    private final File runtimeJar;
    private final PluginJarClassLoader classLoader;
    private final List<Plugin> plugins;

    SideLoadedPluginDeployment(
            String internalName,
            File sourceJar,
            File runtimeJar,
            PluginJarClassLoader classLoader,
            List<Plugin> plugins) {
        this.internalName = internalName;
        this.sourceJar = sourceJar;
        this.runtimeJar = runtimeJar;
        this.classLoader = classLoader;
        this.plugins = Collections.unmodifiableList(new ArrayList<>(plugins));
    }

    String getInternalName() {
        return internalName;
    }

    File getSourceJar() {
        return sourceJar;
    }

    File getRuntimeJar() {
        return runtimeJar;
    }

    PluginJarClassLoader getClassLoader() {
        return classLoader;
    }

    List<Plugin> getPlugins() {
        return plugins;
    }
}
