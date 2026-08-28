# microbot-example-scripts

Reference Gradle module demonstrating how to ship a Microbot plugin as a
standalone JAR that loads via `MicrobotPluginManager.loadSideLoadPlugins()`.

The full how-to is in [`../docs/STANDALONE_SCRIPT_JARS.md`](../docs/STANDALONE_SCRIPT_JARS.md).
This README is the at-a-glance reference.

## TL;DR

```bash
# Build the JAR and copy it into ~/.runelite/microbot-plugins/
./gradlew :microbot-example-scripts:installLocal

# Start the client (the side-loaded plugin shows up automatically)
./gradlew :client:run
```

## What's in here

| File | Role |
|---|---|
| `build.gradle.kts` | Standalone JAR config — all Microbot dependencies are `compileOnly` |
| `src/main/java/com/example/standalone/StandaloneExamplePlugin.java` | `@PluginDescriptor(isExternal = true)` + Guice-injected script/overlay |
| `src/main/java/com/example/standalone/StandaloneExampleScript.java` | Script body — extends `net.runelite.client.plugins.microbot.Script` |
| `src/main/java/com/example/standalone/StandaloneExampleOverlay.java` | Overlay that proves `@Inject Client` works |

## Tasks

| Task | What it does |
|---|---|
| `jar` | Build `StandaloneExamplePlugin.jar` to `build/libs/` |
| `showPluginJar` | Print the absolute path of the produced JAR |
| `installLocal` | Build + copy the JAR into `~/.runelite/microbot-plugins/` |
| `clean` | Remove `build/` |

## Constraints (do not violate)

1. **`compileOnly` everything from `net.runelite:*`, Guice, Lombok, SLF4J, etc.** Bundling them creates two copies of the same class and breaks Guice injection.
2. **JAR filename = Plugin class simple name.** `MicrobotPluginManager.loadSideLoadPlugins()` keys loaded plugins by filename (minus `.jar`) and matches it against the loaded class's simple name.
3. **`@PluginDescriptor(isExternal = true)`** is required. Without it the loader treats the class as bundled and silently skips it.

See [`../docs/STANDALONE_SCRIPT_JARS.md`](../docs/STANDALONE_SCRIPT_JARS.md) for the full explanation, the publishing workflow, debugging tips, and a troubleshooting table.