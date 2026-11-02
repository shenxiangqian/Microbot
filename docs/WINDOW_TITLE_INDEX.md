# `--index` 自定义窗口标题 —— 实现指南

本文档说明如何在 Microbot 分支项目中实现 `--index <值>` 启动参数：客户端启动后把主窗口标题改成指定值（多开时区分账号最常见的用法）。

文档内容自包含，可以直接复制到其它 Microbot 分支仓库实施。若目标分支的 RuneLite 版本较老，路径可能略有差异，文末的「差异适配」一节列出了需要调整的位置。

---

## 目录

- [功能语义](#功能语义)
- [实现总览](#实现总览)
- [步骤 1：解析启动参数](#步骤-1解析启动参数)
- [步骤 2：通过 Guice 传递到 UI 层](#步骤-2通过-guice-传递到-ui-层)
- [步骤 3：ClientUI 注入与状态字段](#步骤-3clientui-注入与状态字段)
- [步骤 4：在窗口显示后应用标题](#步骤-4在窗口显示后应用标题)
- [步骤 5：与其它标题逻辑的优先级](#步骤-5与其它标题逻辑的优先级)
- [步骤 6：调试入口（RuneLiteDebug）](#步骤-6调试入口runelitedebug)
- [完整参考实现](#完整参考实现)
- [边界情况与已知坑](#边界情况与已知坑)
- [验证方法](#验证方法)
- [常见二次调整](#常见二次调整)
- [差异适配](#差异适配)

---

## 功能语义

- `--index` 是**可选**参数，必须带值：`--index 12`、`--index="Account 1"`。
- 值的用途只有一个：**成为主窗口标题的最终字符串**。不做模板替换、不做序号映射、不做截断。
- 参数缺省时视为「未指定」，客户端行为与不传该参数完全一致。
- 标题在**游戏引擎启动之后**才生效（而不是窗口刚显示时），这样可以避免游戏加载失败时标题已经骗人。

---

## 实现总览

需要改动 4 个文件（外加可选的第 5 个）：

| 文件 | 改动内容 |
|------|----------|
| `runelite-client/src/main/java/net/runelite/client/RuneLite.java` | 声明 `--index` 选项、读取值、传给 `RuneLiteModule` |
| `runelite-client/src/main/java/net/runelite/client/RuneLiteModule.java` | 增加构造参数并绑定为 Guice 常量 `@Named("windowTitleOverride")` |
| `runelite-client/src/main/java/net/runelite/client/ui/ClientUI.java` | 注入值、保存状态、在 `show()` 之后轮询应用标题、处理标题优先级 |
| `runelite-client/src/main/java/net/runelite/client/RuneLiteDebug.java` | 调试入口同步支持同一参数（可选） |

数据流：

```
命令行 --index 12
  → RuneLite.main(): String windowTitleOverride
  → new RuneLiteModule(..., windowTitleOverride, ...)
  → Guice: @Named("windowTitleOverride") String
  → ClientUI 构造函数
  → show() 中 frame.setVisible(true) 之后启动 clientThread 轮询
  → 引擎状态离开 UNKNOWN/STARTING 后，在 EDT 上 frame.setTitle(windowTitleOverride)
```

---

## 步骤 1：解析启动参数

文件：`runelite-client/src/main/java/net/runelite/client/RuneLite.java`

在其它 `parser.accepts(...)` 声明旁新增选项声明（必须 `withRequiredArg()`）：

```java
final ArgumentAcceptingOptionSpec<String> indexOpt = parser.accepts("index", "Set the client window title")
    .withRequiredArg()
    .ofType(String.class);
```

解析后取值。**未提供时用空字符串**，后续用 `Strings.isNullOrEmpty` 判断「未指定」：

```java
// Extract FPS setting
Integer targetFps = options.has(fpsOpt) ? options.valueOf(fpsOpt) : null;
String startupScript = options.has(scriptOpt) ? options.valueOf(scriptOpt) : null;
String windowTitleOverride = options.has(indexOpt) ? options.valueOf(indexOpt) : "";
```

在创建 Guice injector 时把它作为构造参数传入 `RuneLiteModule`（位置放在 `noupdate` 之后、`jagexUserHome` 之前即可，只要与 Module 构造签名一致）：

```java
injector = Guice.createInjector(new RuneLiteModule(
    okHttpClient,
    clientLoader,
    runtimeConfigLoader,
    developerMode,
    // ... 其余参数保持不变 ...
    options.has(insecureWriteCredentials),
    options.has("noupdate"),
    windowTitleOverride,
    jagexUserHome.toFile()
));
```

> 注意：调用方传的 `args` 数组会经过 `formatArgumentsForLog` 输出到启动日志。`--index` 不属于敏感参数，会被原样打印（`--index 12`），这是预期行为，不要把它加进 `SENSITIVE_ARGUMENTS`。

---

## 步骤 2：通过 Guice 传递到 UI 层

文件：`runelite-client/src/main/java/net/runelite/client/RuneLiteModule.java`

在构造函数字段区增加字段：

```java
private final boolean noupdate;
private final String windowTitleOverride;
private final File jagexUserHome;
```

在 `configure()` 中绑定为常量：

```java
bindConstant().annotatedWith(Names.named("noupdate")).to(noupdate);
bindConstant().annotatedWith(Names.named("windowTitleOverride")).to(windowTitleOverride);
bind(File.class).annotatedWith(Names.named("runeLiteDir")).toInstance(RuneLite.RUNELITE_DIR);
```

---

## 步骤 3：ClientUI 注入与状态字段

文件：`runelite-client/src/main/java/net/runelite/client/ui/ClientUI.java`

字段区新增三个字段：

```java
private final String title;
private final String windowTitleOverride;
private final boolean windowTitleOverridden;
private boolean windowTitleOverrideApplied;
```

构造函数新增一个 `@Named` 注入参数（放在最后一个参数，避免影响其它注入）：

```java
@Inject
public ClientUI(
    // ... 原有参数 ...
    @Named("safeMode") boolean safeMode,
    @Named("runelite.title") String title,
    @Named("windowTitleOverride") String windowTitleOverride
)
{
    // ... 原有赋值 ...
    this.windowTitleOverride = windowTitleOverride;
    this.windowTitleOverridden = !Strings.isNullOrEmpty(windowTitleOverride);
    this.title = title + (safeMode ? " (safe mode)" : "");
}
```

两个开关的含义：

| 字段 | 含义 |
|------|------|
| `windowTitleOverridden` | 是否传入了 `--index`。用于**关闭**默认的「标题 + 玩家名」逻辑。 |
| `windowTitleOverrideApplied` | 标题是否已经真正被设置过。用于让 `updateFrameConfig` 每次都把标题盖回自定义值。 |

---

## 步骤 4：在窗口显示后应用标题

文件：`runelite-client/src/main/java/net/runelite/client/ui/ClientUI.java`

### 4.1 触发点

在 `show()` 里 `frame.setVisible(true)` **之后**触发（不能放在显示之前，因为游戏可能尚未加载成功甚至加载失败）：

```java
// Show frame
frame.setVisible(true);
if (windowTitleOverridden)
{
    scheduleWindowTitleOnLoggedIn();
}
// On macos setResizable needs to be called after setVisible
frame.setResizable(!config.lockWindowSize());
```

### 4.2 轮询实现

`ClientThread.invokeLater(BooleanSupplier)` 的语义是：**返回 `false` 会在下一个 client tick 重新执行**，返回 `true` 表示结束。因此用它在 client 线程上轮询游戏引擎状态。

```java
/**
 * 等游戏引擎启动后再应用 --index 自定义窗口标题。
 * 若超时（默认 60 秒）仍未启动，则放弃并保持默认标题。
 * 该方法必须在 show() 中 frame.setVisible(true) 之后调用一次。
 */
private void scheduleWindowTitleOnLoggedIn()
{
    final long startTime = System.currentTimeMillis();
    final long TIMEOUT_MS = 60_000L;

    clientThreadProvider.get().invokeLater(() ->
    {
        // 已经应用过则停止轮询
        if (windowTitleOverrideApplied)
        {
            return true;
        }
        // 超时则放弃，保持默认标题
        if (System.currentTimeMillis() - startTime > TIMEOUT_MS)
        {
            log.debug("Timed out waiting for game engine to start; keeping default window title");
            return true;
        }
        // client 字段类型为 Component，需强转为 Client 才能调用 getGameState()
        // 引擎活着就算加载成功：UNKNOWN/STARTING 表示引擎尚未启动，继续等待；
        // 其他状态（LOGIN_SCREEN 及之后）都认为加载成功，可以应用自定义标题。
        final Client gameClient = (Client) client;
        if (gameClient == null || gameClient.getGameState() == GameState.UNKNOWN || gameClient.getGameState() == GameState.STARTING)
        {
            return false; // 引擎还没启动，下一 tick 再试
        }
        // 切回 EDT 线程设置窗口标题（setTitle 是 Swing 调用）
        SwingUtilities.invokeLater(() ->
        {
            if (!windowTitleOverrideApplied)
            {
                windowTitleOverrideApplied = true;
                frame.setTitle(windowTitleOverride);
                log.debug("Applied window title override after game engine loaded: {}", windowTitleOverride);
            }
        });
        return true;
    });
}
```

两个关键点：

1. **`frame.setTitle` 必须在 EDT 上调用。** `ClientThread` 是游戏线程，不是 EDT，所以这里用 `SwingUtilities.invokeLater` 切回去。切勿从 client 线程或后台调度线程直接 `setTitle`。
2. **判定条件是「引擎已启动」而非「已登录」。** `GameState.UNKNOWN` / `GameState.STARTING` 之外的任何状态（`LOGIN_SCREEN`、`LOGGED_IN`、`LOGGING_IN` 等）都视为引擎就绪。如果你的分支希望严格等到登录成功，把判断改为 `gameClient.getGameState() != GameState.LOGGED_IN` 时 `return false` 即可。

---

## 步骤 5：与其它标题逻辑的优先级

ClientUI 里共有三条会写标题的路径，必须让 `--index` 赢。

### 5.1 `onGameStateChanged` —— 登录后追加玩家名

传入 `--index` 时直接跳过，不追加玩家名：

```java
@Subscribe
private void onGameStateChanged(final GameStateChanged event)
{
    if (event.getGameState() != GameState.LOGGED_IN || windowTitleOverridden || !config.usernameInTitle())
    {
        return;
    }

    final Client client = (Client) this.client;
    final ClientThread clientThread = clientThreadProvider.get();

    // Keep scheduling event until we get our name
    clientThread.invokeLater(() ->
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return true;
        }

        final Player player = client.getLocalPlayer();
        if (player == null)
        {
            return false;
        }

        final String name = player.getName();
        if (Strings.isNullOrEmpty(name))
        {
            return false;
        }

        frame.setTitle(title + " - " + name);
        return true;
    });
}
```

### 5.2 `updateFrameConfig` —— 配置变更时刷新标题

优先级顺序：**已应用的 override 标题 > 「标题 + 玩家名」 > 默认标题**。

```java
if (windowTitleOverrideApplied)
{
    frame.setTitle(windowTitleOverride);
}
else if (!windowTitleOverridden && config.usernameInTitle())
{
    final Client client = (Client) this.client;
    final Player player = client.getLocalPlayer();

    String playerName = null;
    if (player != null && !Strings.isNullOrEmpty(player.getName()))
    {
        playerName = player.getName();
    }
    else if (client.getLauncherDisplayName() != null)
    {
        playerName = client.getLauncherDisplayName();
    }

    if (playerName != null)
    {
        frame.setTitle(title + " - " + playerName);
    }
}
else
{
    frame.setTitle(title);
}
```

这一步不能省。否则用户在设置面板里改任何一项配置，标题都会被重置回默认。

---

## 步骤 6：调试入口（RuneLiteDebug）

文件：`runelite-client/src/main/java/net/runelite/client/RuneLiteDebug.java`

如果该分支保留了调试入口，参数要同步支持，否则 `--index` 在 debug 模式下会被解析成未知参数：

```java
final ArgumentAcceptingOptionSpec<String> indexOpt = parser.accepts("index", "Set the client window title")
    .withRequiredArg().ofType(String.class);

// ...
OptionSet options = parser.parse(args);
String windowTitleOverride = options.has(indexOpt) ? options.valueOf(indexOpt) : "";
```

并把它加进 `RuneLiteDebug` 创建 `RuneLiteModule` 时的参数列表。

---

## 完整参考实现

把下面四段代码合起来即构成完整功能，可直接对照移植。

**RuneLite.java**

```java
final ArgumentAcceptingOptionSpec<String> indexOpt = parser.accepts("index", "Set the client window title")
    .withRequiredArg()
    .ofType(String.class);

String windowTitleOverride = options.has(indexOpt) ? options.valueOf(indexOpt) : "";

// injector = Guice.createInjector(new RuneLiteModule(..., windowTitleOverride, ...));
```

**RuneLiteModule.java**

```java
private final String windowTitleOverride;

// configure():
bindConstant().annotatedWith(Names.named("windowTitleOverride")).to(windowTitleOverride);
```

**ClientUI.java**

```java
private final String windowTitleOverride;
private final boolean windowTitleOverridden;
private boolean windowTitleOverrideApplied;

// 构造函数：
@Named("windowTitleOverride") String windowTitleOverride
this.windowTitleOverride = windowTitleOverride;
this.windowTitleOverridden = !Strings.isNullOrEmpty(windowTitleOverride);

// show() 中 frame.setVisible(true) 之后：
if (windowTitleOverridden)
{
    scheduleWindowTitleOnLoggedIn();
}

// 轮询方法见「步骤 4.2」

// onGameStateChanged 中增加 windowTitleOverridden 短路条件
// updateFrameConfig 中按「步骤 5.2」调整优先级
```

---

## 边界情况与已知坑

### 坑 1：60 秒超时后静默丢弃标题

如果游戏更新下载慢或引擎启动失败，轮询在 60 秒后直接 `return true` 停止，标题会一直保持默认值，**`--index` 的值等于丢了**，且只在 debug 日志里留痕。

处理建议：
- 把 `TIMEOUT_MS` 调大（例如 180 秒），或
- 改成超时后仍然应用标题（把 `return true` 换成 `return false` 之前的降级分支），或
- 超时时打 `log.warn` 而不是 `log.debug`，便于排查。

### 坑 2：与 BreakHandler 的标题冲突

Microbot 的休息处理器（`BreakHandlerScript` / `BreakHandlerV2Script`）会在插件启动时抓取当前标题作为基准：

```java
originalWindowTitle = ClientUI.getFrame().getTitle();
```

休息期间再改成 `原标题 - 休息状态: 剩余时间`。由于 `--index` 标题是在引擎启动后才应用的，**两者存在竞态**：

- 如果抓到的是默认标题，休息结束后会把 `--index` 标题覆盖掉；
- 如果抓到的是 `--index` 标题，最终会变成 `自定义标题 - Break: 00:30`。

规避方式（任选其一）：
- 在插件启动时不要用 `frame.getTitle()` 当基准，改从 `ClientUI` 暴露一个 getter 读取当前生效的 override 值；
- 或在休息处理器恢复标题时判断当前标题是否是 override 生效状态。

### 坑 3：Swing 线程约束

任何在后台线程（scheduler、client thread）里调用 `frame.setTitle(...)` 都违反 Swing 单线程规则，轻则标题不刷新，重则抛 `IllegalStateException` 或窗口边框渲染错乱。必须包 `SwingUtilities.invokeLater(...)`。

### 坑 4：默认标题初始化

`init()` 里创建 frame 时会设置一次默认标题（`title` 或 `title - 启动器显示名`）。这发生在 `show()` 之前，属于预期行为：自定义标题会在引擎启动后覆盖它。

---

## 验证方法

```bash
# 编译
./gradlew :client:compileJava

# 带参数启动
./gradlew :client:run --args="--index 12"

# 或直接跑 shaded jar
java -jar client-<version>-SNAPSHOT-shaded.jar --index 12
```

检查点：

1. 启动日志中应出现 `--index 12`（未脱敏）。
2. 窗口刚出现时标题是默认值；游戏引擎启动后标题变为 `12`。
3. 打开设置面板改任意配置，标题应仍然是 `12`（验证 `updateFrameConfig` 优先级）。
4. 登录进游戏后，标题不应变成 `RuneLite - 玩家名`（验证 `onGameStateChanged` 短路）。

日志级别开到 DEBUG 可以看到 `Applied window title override after game engine loaded: 12`。

---

## 常见二次调整

| 需求 | 改哪里 |
|------|--------|
| 延长等待引擎启动的时限 | `scheduleWindowTitleOnLoggedIn()` 中的 `TIMEOUT_MS` |
| 严格等到登录成功才设标题 | 把 `gameClient.getGameState() == UNKNOWN \|\| == STARTING` 改为 `!= GameState.LOGGED_IN` |
| 标题加前缀（如 `Microbot - `） | 在 `frame.setTitle(windowTitleOverride)` 处拼接，或在读取参数处拼接 |
| 支持 `--index` 与账号序号映射 | 在 `RuneLite` 读取参数后根据传入值查表映射成最终标题字符串 |
| 标题里带上角色名 | 应用标题的分支里改成 `windowTitleOverride + " - " + player.getName()`（注意 `getLocalPlayer()` 可能为 null） |

---

## 差异适配

如果目标分支与本文档对应的版本不同，优先检查这几处：

| 位置 | 可能差异 | 处理 |
|------|----------|------|
| `RuneLite.java` | 参数解析用的是 `joptsimple`，也可能换成了别的库 | 保持「一个必填 String 参数 + 缺省空串」这个语义即可 |
| `RuneLiteModule.java` | 构造参数顺序可能不同 | 参数位置以 `bindConstant` 的 `Names.named` 为准，两边保持一致 |
| `ClientUI` | 可能有 `client` 字段为 `Client` 而非 `Component` | 那就不需要 `(Client) client` 强转 |
| `ClientUI.show()` | 可能拆成了 `show()` / `showLater()` 等多个方法 | 只要保证 `frame.setVisible(true)` 之后才触发轮询即可 |
| `ClientThread.invokeLater(BooleanSupplier)` | 老版本可能只有 `invokeLater(Runnable)` | 需要改成用 `invokeLater` + `EventBus` 订阅 `GameStateChanged` 的方式轮询 |
| `updateFrameConfig` | 方法名可能不同 | 找到「窗口配置刷新时重设标题」的方法，把 override 分支插到最前面 |
| BreakHandler | 目标分支可能没有这个插件 | 无需处理坑 2 |

如果目标分支连 `ClientUI` 的标题逻辑都没有（例如只保留了上游 RuneLite 的 `frame.setTitle(...)`），最小可用实现就是「步骤 1 + 步骤 2 + 步骤 3 + 步骤 4」，步骤 5 可选。
