# ADR 0005: Standalone Script JAR Distribution

- Status: Accepted (2026-08-27)

## Context

Microbot ships hundreds of automation scripts inside `runelite-client`.
Plugins that want to live outside the client — community plugins, scripts
under their own versioning, private forks — currently have no supported
packaging path. The plugin loading mechanism exists (`MicrobotPluginManager`
+ `PluginJarClassLoader`), but no Gradle template shows how to build a
plugin against it.

A community plugin author has to:

1. Reverse-engineer which classes are safe to bundle vs. which must come from
   the parent classloader.
2. Figure out the right Maven coordinates for the Microbot client (the
   obvious `net.runelite:client` is the *stock RuneLite* client and lacks
   every Microbot class).
3. Wire up `compileOnly` correctly so Guice injection still works through
   the parent injector.
4. Match the JAR filename to the `@PluginDescriptor`-annotated class name
   so the loader doesn't reject it.

Every existing external plugin (chsami/Microbot-Hub, bgatfa/bgatfa-plugins,
…) solves this differently. The result is fragile plugins that break every
time the client changes.

## Decision

Introduce `microbot-example-scripts/` as the reference build module for
standalone plugin JARs. The module:

- Resolves the Microbot client in priority order: explicit `-PmicrobotClientPath`,
  then sibling `-PmicrobotDir=.../Microbot`, then GitHub Releases download.
- Marks **every** RuneLite / Microbot / Guice / SLF4J dependency as
  `compileOnly`. Bundling them creates two copies of the same class and
  breaks Guice injection through `PluginJarClassLoader`'s child-first
  delegation.
- Names the JAR after the `@PluginDescriptor`-annotated class so it
  matches the loader's filename-keyed de-duplication.
- Provides three Gradle tasks: `jar`, `showPluginJar`, `installLocal`
  (which copies the JAR into `~/.runelite/microbot-plugins/`).

Document the pattern in `docs/STANDALONE_SCRIPT_JARS.md`. Reference it
from `README.md`, `docs/README.md`, `docs/INDEX.md`, and
`docs/development.md` so plugin authors find it without spelunking.

## Consequences

- New contributors have a working template to clone and adapt.
- The Maven-coordinate ambiguity (which "client"?) is captured in the build
  script with a clear error message when resolution fails.
- The `installLocal` task lowers the build → run loop to a single command.
- The reference module proves the loader needs no changes — it works with
  the existing `MicrobotPluginManager.loadSideLoadPlugins()` flow.
- Standalone plugins can be versioned independently of the client, enabling
  proper plugin-hub distribution.
- The reference module's `PluginDescriptor(isExternal = true)` plus the
  matching JAR filename is the contract — no Microbot core changes
  required.