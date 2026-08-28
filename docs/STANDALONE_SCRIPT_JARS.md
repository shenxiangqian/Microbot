# Standalone Script JARs

Build, ship, and run Microbot plugins as standalone JARs that load alongside
the main client — without recompiling the client, without checking the plugin
source into the main repository, and without modifying the plugin loader.

This document is the complete how-to for the `microbot-example-scripts`
Gradle module. Read it end-to-end if you're publishing external scripts; skim
section 2 + 4 if you're just consuming one.

---

## 1. Background

Microbot already has a side-loading mechanism for plugins distributed through
the [Microbot Hub](https://github.com/chsami/Microbot-Hub). The relevant code
lives in:

```
runelite-client/src/main/java/net/runelite/client/plugins/microbot/externalplugins/
├── MicrobotPluginManager.java      # Loader, refresh, install, remove
└── PluginJarClassLoader.java       # URLClassLoader with parent delegation
```

The runtime contract is simple:

1. JARs live in `~/.runelite/microbot-plugins/`.
2. Each JAR's filename (minus `.jar`) must match the simple name of a `@PluginDescriptor`-annotated class inside it.
3. That class's `@PluginDescriptor(isExternal = true)` must be set.
4. The class extends `net.runelite.client.plugins.Plugin`.
5. Guice dependencies (`@Inject ClientThread`, `ConfigManager`, `OverlayManager`, …) are satisfied by the parent's injector — the JAR only needs to declare them at *compile* time.

`MicrobotPluginManager.loadSideLoadPlugins()` runs on every client startup
(see `RuneLite.java` line ~542). It also runs on profile changes and on the
`ExternalPluginsChanged` event. You don't have to wire any of this up — if
you satisfy the contract above, the plugin shows up in the configuration
panel and starts/stops like a bundled plugin.

The `microbot-example-scripts` module demonstrates the build setup. It
compiles to a separate, self-contained JAR that can be dropped into the
side-load directory.

---

## 2. Module Layout

```
microbot-example-scripts/
├── build.gradle.kts                            # Standalone JAR build config
├── README.md                                   # Quick reference for this module
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── standalone/
                        ├── StandaloneExamplePlugin.java   # @PluginDescriptor(isExternal=true)
                        ├── StandaloneExampleScript.java   # extends Script
                        └── StandaloneExampleOverlay.java  # proves DI wiring
```

The package name (`com.example.standalone`) does **not** need to live under
`net.runelite.client.plugins.microbot`. In fact, side-loaded plugins are
expected to use a different package — that's how the loader knows they aren't
already bundled.

---

## 3. Build Configuration

The full file is at `microbot-example-scripts/build.gradle.kts`. Highlights:

```kotlin
// Read the same versions the main client uses.
val rootProps = file("../gradle.properties").inputStream().use {
    Properties().apply { load(it) }
}
val microbotVersion = rootProps.getProperty("microbot.version")
val runeliteVersion = rootProps.getProperty("project.build.version")

dependencies {
    // compileOnly — the parent classloader provides these at runtime.
    compileOnly("net.runelite:runelite-api:$runeliteVersion")
    compileOnly(files(resolvedClientJar))  // see "Resolving the Microbot client"
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    compileOnly("com.google.inject:guice:4.1.0")
    compileOnly("com.google.guava:guava:23.2-jre")
    compileOnly("javax.inject:javax.inject:1")
    compileOnly("org.slf4j:slf4j-api:1.7.25")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("StandaloneExamplePlugin")  // -> StandaloneExamplePlugin.jar
    manifest {
        attributes["Plugin-Name"] = "StandaloneExamplePlugin"
        attributes["Plugin-Version"] = project.version.toString()
        attributes["Microbot-Min-Version"] = rootProps.getProperty("microbot.version")
    }
}
```

### Resolving the Microbot client (the gotcha)

The Microbot client is **not** published as `net.runelite:client` — that
coordinate is reserved for stock RuneLite and is missing every Microbot class
(`Microbot`, `Script`, `PluginDescriptor.isExternal`, `Rs2Player`, …).
Compile against the wrong artifact and the build fails with a wall of
"package does not exist" errors.

`build.gradle.kts` resolves the client JAR in priority order:

1. **`-PmicrobotClientPath=/abs/path/to/microbot-<version>.jar`** — explicit
   pointer to a prebuilt JAR. Use this from CI or when you have a specific
   build cached locally.
2. **`-PmicrobotDir=/abs/path/to/Microbot`** — sibling Microbot checkout.
   The build looks for either `runelite-client/build/libs/microbot-<microbot.version>.jar`
   or `runelite-client/build/libs/client-<runelite.version>-shaded.jar` under
   that directory.
3. **Default fallback** — the `downloadMicrobotClient` task fetches
   `microbot-<microbot.version>.jar` from
   `https://github.com/chsami/Microbot/releases/<version>/` and caches it
   under `build/`. The download runs automatically the first time you build.

When developing side-by-side with the main Microbot checkout, the most
ergonomic command is:

```bash
./gradlew :microbot-example-scripts:installLocal -PmicrobotDir=../Microbot
```

### Why `compileOnly`?

`PluginJarClassLoader` extends `URLClassLoader` and uses **child-first**
delegation. If your JAR contains `net.runelite.client.plugins.Plugin`, the
loader sees *two* `Plugin` classes (yours and the parent's), and Guice's
`Key.get()` calls return the wrong one. Result: instantiations fail with
`com.google.inject.ConfigurationException: … was registered multiple times`
or `LinkageError: loader (instance of …) previously initiated loading for a
different type with name "net/runelite/client/plugins/Plugin"`.

`compileOnly` keeps the JAR lean and avoids the split-brain. Every class the
plugin needs at runtime is provided by the parent Microbot classloader.

### Settings hook

`settings.gradle.kts` includes the new module:

```kotlin
include("microbot-example-scripts")
project(":microbot-example-scripts").projectDir = file("./microbot-example-scripts")
```

Note: this is a regular `include`, **not** an `includeBuild`. The module
depends on the published shaded JAR — it doesn't need sources from the
parent project. That means building `:microbot-example-scripts` alone doesn't
trigger a `:client` rebuild.

---

## 4. Build, Install, Run

### One-shot install (builds the JAR and drops it into the side-load directory)

```bash
./gradlew :microbot-example-scripts:installLocal
```

Output:

```
> Task :microbot-example-scripts:installLocal
Plugin JAR: <repo>/microbot-example-scripts/build/libs/StandaloneExamplePlugin.jar
> Task :microbot-example-scripts:installLocal
Copied ... to ~/.runelite/microbot-plugins/StandaloneExamplePlugin.jar
```

### Just build

```bash
./gradlew :microbot-example-scripts:jar
ls microbot-example-scripts/build/libs/
# StandaloneExamplePlugin.jar
```

### Print the JAR path

```bash
./gradlew :microbot-example-scripts:showPluginJar
```

### Manual install (if you don't trust `installLocal`)

```bash
# PowerShell (Windows)
Copy-Item .\microbot-example-scripts\build\libs\StandaloneExamplePlugin.jar `
          $env:USERPROFILE\.runelite\microbot-plugins\

# Bash (Linux / macOS / WSL)
cp ./microbot-example-scripts/build/libs/StandaloneExamplePlugin.jar \
   ~/.runelite/microbot-plugins/
```

### Start the client

```bash
./gradlew :client:run
```

On startup, look for these log lines (Logback `INFO`):

```
Plugin loaded StandaloneExamplePlugin
```

…or, if the JAR couldn't be loaded:

```
Error loading side-loaded plugin: StandaloneExamplePlugin
```

### Enable the plugin in the UI

1. Open the Microbot configuration panel (the side-bar gear icon, or use the
   `microbot` config hub).
2. Search for "Standalone Example".
3. Toggle it on.

You should see:

- A `[StandaloneExample] running` status line in the side panel.
- The plugin listed in the **External Plugins** section of the config hub,
  with the version + minimum client version from `@PluginDescriptor`.

---

## 5. Plugin Author Rules

These rules apply to **both** bundled and side-loaded plugins. They come from
the project's non-negotiable rules (`AGENTS.md`) and the script-authoring
guide (`runelite-client/src/main/java/net/runelite/client/plugins/microbot/AGENTS.md`).

### Annotations

```java
@PluginDescriptor(
    name = "Standalone Example",          // Shown in the config panel.
    description = "What the plugin does.",
    tags = {"example", "microbot"},       // Used by the in-panel search.
    enabledByDefault = false,             // Don't auto-start until the user opts in.
    isExternal = true,                    // REQUIRED for side-loaded plugins.
    version = "1.0.0",                    // Used for the "outdated" check.
    minClientVersion = "2.0.0",           // The loader rejects older clients.
    authors = {"Your Name"}
)
public class StandaloneExamplePlugin extends Plugin { ... }
```

> **Do not** check `isExternal()` at runtime to gate behavior — `Microbot`
> treats side-loaded and bundled plugins identically once they're loaded.

### Threading

- Script loops run on `Script.scheduledExecutorService`. Never block the
  client thread.
- Use `Microbot.getClientThread().runOnClientThreadOptional(...)` (or
  `.invoke(...)`) for any read/write to `Client`, `Widget`, `MenuEntry`, or
  anything else that requires the client thread.
- Use `sleepUntil(condition, timeoutMs)`. Never `Thread.sleep(...)` to wait
  on game state.

### Caches

- Use `Microbot.getRs2NpcCache()`, `Microbot.getRs2PlayerCache()`,
  `Microbot.getRs2TileObjectCache()`, etc.
- Call `.query().<filters>` or `.getStream()` — **never** instantiate a cache
  yourself.

### Dependency injection

- `@Inject` works exactly like in a bundled plugin.
- Constructor injection is preferred for `Overlay` and `Script` classes that
  don't extend `Plugin`. Field injection is fine for the `Plugin` itself.
- Don't reach for `Microbot.getInjector()` directly — the loader wires a
  child injector for you.

### Lifecycle

- `startUp()` and `shutDown()` must be idempotent.
- Cancel scheduled futures in `shutDown()` — leaving them running leaks
  threads and keeps the plugin "active" in the UI.
- Unregister `@Subscribe` handlers if you add them dynamically.

---

## 6. Debugging

### Run the client with JDWP attached

```bash
./gradlew :client:runDebug
# …then in IntelliJ: Run → Attach to Process → localhost:5005
```

You'll need to set breakpoints in **two** places to debug a side-loaded
plugin:

1. In the plugin's source code (this module) — the same as any normal Java
   project.
2. In `MicrobotPluginManager.loadSideLoadPlugin()` if the plugin fails to
   load at all — this is where class-loading errors get caught and logged.

### Watch the classloader

Add a temporary breakpoint in `PluginJarClassLoader.loadClass(String)` to
confirm the loader is using your JAR and falling back to the parent for
framework classes.

### Inspect the loaded JAR

```bash
# List contents
unzip -l StandaloneExamplePlugin.jar

# Look for any unexpected entries (signing files, IDE metadata, ...)
unzip -l StandaloneExamplePlugin.jar | grep -v 'com/example/'
```

A healthy JAR contains:

- `META-INF/MANIFEST.MF` (with the attributes we set)
- `com/example/standalone/*.class`
- Lombok-generated methods (if you used `@Slf4j` etc.)

…and **never** `net/runelite/**`, `com/google/inject/**`, or
`META-INF/*.SF`/`*.RSA`/`*.DSA` (signing files trip up the loader).

### IntelliJ setup

1. Open the root `build.gradle.kts` as a project.
2. Gradle will pick up the new module automatically because of the
   `settings.gradle.kts` change.
3. Add a **Gradle** run configuration for
   `:microbot-example-scripts:installLocal`.
4. Add a second **Gradle** run configuration for `:client:run`.
5. Debug the client and set breakpoints in the plugin. After `installLocal`
   replaces the JAR, click the refresh button beside the script search field.

The refresh action stops and unloads every JAR from `microbot-plugins`, closes
its class loader, and loads a fresh runtime copy. Reloaded plugins remain
disabled and must be enabled manually. Plugin configuration is preserved, but
in-memory script state is not. Side-loaded plugins must not set
`alwaysOn = true` because a refreshed plugin must remain stopped.

The client loads a shadow copy under `~/.runelite/cache/microbot-plugin-runtime/`
so the source JAR can be overwritten while the client is running. If a script
does not release its executor threads during unload, that plugin is blocked
from loading again until the client restarts.

Plugins marked `isExternal = true` are launched from the play button in the
client title bar. The selection dialog lists only external plugins owned by the
Microbot side-load manager; native Microbot plugins remain in the Installed
sidebar. While one external script is running, the play button is replaced by
stop and restart controls. Restart unloads only the selected script JAR, loads
the current source JAR, and starts the matching plugin class again. The client
remembers the most recently started external script for the current session, so
the restart control remains available after that script is stopped manually.

---

## 7. Updating / Removing

### Update

1. Bump `version = "1.0.1"` in `@PluginDescriptor`.
2. Rebuild: `./gradlew :microbot-example-scripts:installLocal`
3. Click the refresh button beside the script search field, then enable the
   updated plugin manually.

### Remove

```bash
rm ~/.runelite/microbot-plugins/StandaloneExamplePlugin.jar
```

Then click the refresh button. The plugin disappears from the config panel.

### Clean rebuild

```bash
./gradlew :microbot-example-scripts:clean
./gradlew :microbot-example-scripts:installLocal
```

---

## 8. Publishing to the Microbot Hub

If you want to distribute the JAR through the [Microbot Hub](https://github.com/chsami/Microbot-Hub)
so it shows up in the **External Plugins** UI with one-click install:

1. Fork `chsami/Microbot-Hub`.
2. Add an entry in `plugins.json` with:
   - `internalName` matching your Plugin class's simple name (`StandaloneExamplePlugin`).
   - `displayName`, `description`, `tags`.
   - `version` matching `@PluginDescriptor(version=…)`.
   - `sha256` of the built JAR.
   - `jarUrl` pointing at a release asset on your fork.
3. Tag a release; the hub mirrors it.

The client will then offer the plugin in the External Plugins tab. When the
user clicks install, `MicrobotPluginManager.installPlugin()` downloads the
JAR, verifies the SHA-256, and calls `loadSideLoadPlugin()`. From there,
behavior is identical to a manually-dropped JAR.

---

## 9. Common Pitfalls

| Symptom | Cause |
|---|---|
| `ClassNotFoundException: net.runelite.client.plugins.Plugin` at load time | The shaded client JAR isn't on the classpath — usually means you ran the plugin in a separate JVM without launching Microbot first. |
| `LinkageError: loader … previously initiated loading for a different type` | You bundled a `compileOnly` class into the JAR. Re-check the `exclude` list in `tasks.named<Jar>("jar")`. |
| Plugin not in the config panel | Filename ≠ class simple name, or `isExternal = true` is missing. |
| `Plugin XXX requires client version YYY or higher` | Bump `@PluginDescriptor(minClientVersion = …)` or upgrade the client. |
| `Incompatible plugin found: …` and the JAR is deleted | The JAR contains bytecode that targets a newer Java version than the client supports. Set `options.release = 11`. |
| `@Inject` fields are `null` | The class isn't annotated with `@PluginDescriptor` *and* `extends Plugin` — both are required for the loader to instantiate it. |
| Guice configuration exception about duplicate bindings | You accidentally re-declared a parent binding. Move it to `compileOnly` or delete it entirely. |

---

## 10. Reference

- Loader source: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/externalplugins/MicrobotPluginManager.java`
- Class loader: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/externalplugins/PluginJarClassLoader.java`
- `@PluginDescriptor` source: `runelite-client/src/main/java/net/runelite/client/plugins/PluginDescriptor.java`
- Root non-negotiable rules: `AGENTS.md`
- Script-authoring rules: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/AGENTS.md`
- Queryable cache API: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md`
- Composite build rationale: `docs/decisions/adr-0002-composite-build-structure.md`
- Shaded packaging rationale: `docs/decisions/adr-0004-shaded-distribution-packaging.md`
