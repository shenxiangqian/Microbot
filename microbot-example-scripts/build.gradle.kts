/*
 * Copyright (c) 2024, Microbot
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

import java.util.Properties
import java.net.URI

// Pull versions from the root gradle.properties so this module tracks the
// parent build without duplicating constants.
val rootProps = file("../gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}
val microbotVersion: String = rootProps.getProperty("microbot.version") ?: "0.0.0"
val runeliteVersion: String = rootProps.getProperty("project.build.version") ?: "1.12.36"
val lombokVersion: String = rootProps.getProperty("lombok.version") ?: "1.18.30"

// ---------------------------------------------------------------------------
// Microbot client resolution.
//
// The Microbot client is NOT published as `net.runelite:client` — that
// coordinate is reserved for stock RuneLite and is missing every Microbot
// class (Microbot, Script, PluginDescriptor.isExternal, Rs2Player, ...).
//
// Three resolution strategies, in priority order:
//
//   1. `-PmicrobotClientPath=/abs/path/to/microbot-<version>.jar` — explicit
//      pointer to a prebuilt client. Use this when you have a specific
//      shaded JAR from another checkout or a CI cache.
//   2. `-PmicrobotDir=/abs/path/to/Microbot` — sibling Microbot checkout.
//      We compile against its just-built `:client:shadowJar`.
//   3. Otherwise: download `microbot-<microbot.version>.jar` from the
//      Microbot GitHub Releases page (this is what end-user plugins do).
//
// Strategies 1 and 2 keep CI / offline builds working; strategy 3 makes the
// module self-contained for first-time users.
// ---------------------------------------------------------------------------

val microbotClientPath: String? =
    (project.findProperty("microbotClientPath") as String?)?.takeIf { it.isNotBlank() }

val microbotDir: String? =
    (project.findProperty("microbotDir") as String?)?.takeIf { it.isNotBlank() }

/**
 * Returns the path to the Microbot client JAR to compile against. Pure
 * resolver — does NOT download anything. Callers that need the file to exist
 * on disk should depend on [downloadMicrobotClient] before invoking this.
 */
fun resolveClientJar(): File = when {
    microbotClientPath != null -> file(microbotClientPath).also {
        require(it.exists()) { "microbotClientPath=$microbotClientPath does not exist" }
    }
    microbotDir != null -> {
        val named = file("$microbotDir/runelite-client/build/libs/microbot-${microbotVersion}.jar")
        if (named.exists()) {
            named
        } else {
            val shaded = file("$microbotDir/runelite-client/build/libs/client-$runeliteVersion-shaded.jar")
            require(shaded.exists()) {
                "Could not find a Microbot client JAR under $microbotDir. " +
                    "Build it first with `(cd $microbotDir && ./gradlew :client:assemble)`."
            }
            shaded
        }
    }
    else -> layout.buildDirectory.file("microbot-${microbotVersion}.jar").get().asFile
}

/** Download the Microbot client JAR from GitHub Releases into the build cache. */
val downloadMicrobotClient = tasks.register("downloadMicrobotClient") {
    group = "setup"
    description = "Download the Microbot client JAR from GitHub Releases"

    val target = layout.buildDirectory.file("microbot-${microbotVersion}.jar").get().asFile
    outputs.file(target)

    doLast {
        if (target.exists()) {
            logger.lifecycle("Reusing cached Microbot client: ${target.absolutePath}")
            return@doLast
        }
        target.parentFile.mkdirs()
        val url = "https://github.com/chsami/Microbot/releases/download/$microbotVersion/microbot-$microbotVersion.jar"
        logger.lifecycle("Downloading Microbot client from $url …")
        val uri = URI.create(url)
        uri.toURL().openStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("Saved Microbot client to ${target.absolutePath} (${target.length()} bytes)")
    }
}

plugins {
    java
    `java-library`
    alias(libs.plugins.lombok)
}

group = "com.example.microbot"
version = "1.0.0"

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    maven(uri("https://repo.runelite.net")) {
        name = "rrn"
        content {
            includeGroupAndSubgroups("net.runelite")
        }
    }
    mavenCentral {
        content { excludeGroupAndSubgroups("net.runelite") }
    }
}

// CRITICAL: every RuneLite / Microbot dependency below must be `compileOnly`.
//
// At runtime the parent Microbot classloader (Microbot's main classpath, loaded
// by the shaded `microbot-<version>.jar`) supplies:
//
//   * net.runelite.client.plugins.Plugin, PluginDescriptor, PluginManager
//   * net.runelite.client.plugins.microbot.Microbot, Script, BlockingEventManager
//   * net.runelite.client.plugins.microbot.util.* (Rs2Player, Rs2Inventory, ...)
//   * net.runelite.api.* (Client, GameState, WorldPoint, ...)
//   * Guice, Guava, EventBus, SLF4J, Jackson, Gson, etc.
//
// PluginJarClassLoader uses child-first delegation and falls back to the parent
// for any class that isn't in the JAR — that's how DI and type lookup work.
//
// Bundling any of these inside the standalone JAR would create two copies of the
// same class, break Guice injection, and trigger LinkageErrors at runtime.
dependencies {
    // RuneLite API — published by RuneLite upstream and mirrored on the
    // repo.runelite.net Maven repository.
    compileOnly("net.runelite:runelite-api:$runeliteVersion")

    // Microbot client — a `files()`-resolved compileOnly dep. The artifact is
    // the shaded `microbot-<version>.jar`; it's read at compile time only and
    // never copied into the plugin JAR. The plugin's `compileOnly` scope
    // guarantees the same classes will resolve through the parent classloader
    // when the plugin is loaded by MicrobotPluginManager.
    compileOnly(files(provider { resolveClientJar() }))

    // Annotation helpers used by the plugin source. Direct declarations avoid
    // leaning on the version catalog, which is intentionally narrow.
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    compileOnly("com.google.inject:guice:4.1.0")
    compileOnly("com.google.guava:guava:23.2-jre")
    compileOnly("javax.inject:javax.inject:1")
    compileOnly("org.slf4j:slf4j-api:1.7.25")

    testImplementation(libs.junit)
}

lombok {
    version.set(lombokVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 11
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    // Make sure the compile classpath always includes the (downloaded or
    // pre-built) client JAR, even when the user invokes `compileJava` without
    // depending on the download task explicitly.
    dependsOn(downloadMicrobotClient)
}

// Build the standalone plugin JAR.
//
// MicrobotPluginManager.loadSideLoadPlugins() keys external plugins by the JAR
// filename minus `.jar`, and the loaded Plugin class's simple name must match
// that key. We name the artifact after the Plugin class
// (`StandaloneExamplePlugin`) so the file lands at
// `~/.runelite/microbot-plugins/StandaloneExamplePlugin.jar`.
val pluginJar = tasks.named<Jar>("jar") {
    archiveBaseName.set("StandaloneExamplePlugin")
    archiveClassifier.set("")
    archiveVersion.set("")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Plugin-Name"] = "StandaloneExamplePlugin"
        attributes["Plugin-Version"] = project.version.toString()
        attributes["Microbot-Min-Version"] = microbotVersion
        attributes["Built-By"] = "microbot-example-scripts Gradle module"
    }

    // Keep the JAR diff-friendly across rebuilds and OS platforms.
    exclude("**/.gitignore")
    exclude("**/.git/**")
    exclude("**/.idea/**")
    exclude("**/.vscode/**")
    exclude("**/*.iml")
    exclude("**/module-info.class")
    exclude("META-INF/INDEX.LIST")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

// Convenience task: build the JAR and copy it into `~/.runelite/microbot-plugins/`.
// MicrobotPluginManager scans that directory on startup.
val installLocalPlugin = tasks.register<Copy>("installLocal") {
    group = "distribution"
    description = "Build the plugin JAR and copy it into ~/.runelite/microbot-plugins/"

    dependsOn(pluginJar)

    val runeliteDir = file("${System.getProperty("user.home")}/.runelite/microbot-plugins")
    from(pluginJar.flatMap { it.archiveFile })
    into(runeliteDir)
    rename { "StandaloneExamplePlugin.jar" }

    doFirst {
        if (!runeliteDir.exists() && !runeliteDir.mkdirs()) {
            logger.warn("Could not create ${runeliteDir.absolutePath} — start the Microbot client once to bootstrap ~/.runelite/")
        }
    }
}

// Convenience task: print the absolute path of the produced JAR.
val showPluginJar = tasks.register("showPluginJar") {
    group = "verification"
    description = "Print the absolute path of the produced plugin JAR"

    dependsOn(pluginJar)
    doLast {
        val jar = pluginJar.get().archiveFile.get().asFile
        logger.lifecycle("Plugin JAR: ${jar.absolutePath}")
    }
}

// Reproducible artifacts (matches common.settings.gradle.kts behavior).
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<Test>("test") {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}