# Session-based 自动登录实现方案

## 需求
通过命令行参数 `--session-id` 和 `--character-id` 实现直接登录，跳过传统的用户名/密码认证流程。

## 实现步骤

### 步骤 1: 修改 RuneLite.java 添加命令行参数

**文件:** `runelite-client/src/main/java/net/runelite/client/RuneLite.java`

**位置:** 在 `main()` 方法中，约第 204-228 行参数定义部分

**添加内容:**
```java
// 在现有参数定义之后添加（约 217 行之后）
final ArgumentAcceptingOptionSpec<String> sessionIdOpt = parser.accepts("session-id", "Session ID for direct login")
    .withRequiredArg()
    .ofType(String.class);

final ArgumentAcceptingOptionSpec<String> characterIdOpt = parser.accepts("character-id", "Character ID for direct login")
    .withRequiredArg()
    .ofType(String.class);
```

**解析参数并传递给登录模块（约 280 行之后，在启动流程中）:**
```java
// 在 OptionSet options = parser.parse(args); 之后
// 提取 session-id 和 character-id
String sessionId = options.has(sessionIdOpt) ? options.valueOf(sessionIdOpt) : null;
String characterId = options.has(characterIdOpt) ? options.valueOf(characterIdOpt) : null;
```

**将参数传递到依赖注入容器（在创建 Injector 时）:**
```java
// 在创建 injector 之前，需要将这些值存储到一个静态位置或通过 Module 传递
// 方案 A: 使用静态变量临时存储
if (sessionId != null && characterId != null) {
    DirectSessionLogin.setSessionCredentials(sessionId, characterId);
}
```

---

### 步骤 2: 创建 DirectSessionLogin 类

**文件:** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/security/DirectSessionLogin.java`

```java
package net.runelite.client.plugins.microbot.util.security;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles direct session-based login using session_id and character_id.
 * Used when launching client with --session-id and --character-id parameters.
 */
@Slf4j
public final class DirectSessionLogin {

    private static String sessionId = null;
    private static String characterId = null;
    private static final AtomicBoolean loginAttempted = new AtomicBoolean(false);

    private DirectSessionLogin() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Sets session credentials from command line arguments.
     * Must be called before client initialization.
     */
    public static void setSessionCredentials(String sessionIdArg, String characterIdArg) {
        sessionId = sessionIdArg;
        characterId = characterIdArg;
        log.info("Direct session login credentials configured");
    }

    /**
     * Returns true if session credentials have been provided via command line.
     */
    public static boolean hasSessionCredentials() {
        return sessionId != null && characterId != null;
    }

    /**
     * Attempts to perform a direct session login.
     * This should be called after client is initialized and on LOGIN_SCREEN state.
     * 
     * @return true if login was attempted successfully
     */
    public static boolean attemptLogin() {
        if (!hasSessionCredentials()) {
            log.debug("No session credentials available for direct login");
            return false;
        }

        if (loginAttempted.getAndSet(true)) {
            log.debug("Direct session login already attempted");
            return false;
        }

        Client client = Microbot.getClient();
        if (client == null) {
            log.warn("Cannot perform direct session login - client not initialized");
            return false;
        }

        GameState state = client.getGameState();
        if (state != GameState.LOGIN_SCREEN && state != GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            log.debug("Cannot perform direct session login - not on login screen (state: {})", state);
            return false;
        }

        try {
            log.info("Attempting direct session login with session_id: {}, character_id: {}", 
                maskSessionId(sessionId), characterId);

            // 使用反射调用 Client.setAccountHash 方法
            // 这是 RuneLite injected-client 提供的方法，用于直接设置 session
            boolean success = setAccountHashViaReflection(client, sessionId, characterId);

            if (success) {
                log.info("Direct session login initiated successfully");
                return true;
            } else {
                log.error("Failed to set account hash via reflection");
                return false;
            }
        } catch (Exception e) {
            log.error("Error during direct session login", e);
            return false;
        }
    }

    /**
     * Uses reflection to call Client.setAccountHash() method.
     * This method is provided by the injected-client and directly sets the session.
     */
    private static boolean setAccountHashViaReflection(Client client, String sessionId, String characterId) {
        try {
            // 方法 1: 尝试调用 setAccountHash(String sessionId)
            var method = client.getClass().getMethod("setAccountHash", String.class);
            method.invoke(client, sessionId);
            
            // 方法 2: 如果需要同时设置 character ID，可能需要额外调用
            // var setCharMethod = client.getClass().getMethod("setCharacterId", String.class);
            // setCharMethod.invoke(client, characterId);
            
            log.debug("Successfully invoked setAccountHash with session_id");
            return true;
        } catch (NoSuchMethodException e) {
            log.error("setAccountHash method not found on Client - injected-client may not support this", e);
            return false;
        } catch (Exception e) {
            log.error("Failed to invoke setAccountHash via reflection", e);
            return false;
        }
    }

    /**
     * Masks session ID for safe logging (shows first 8 chars only).
     */
    private static String maskSessionId(@Nullable String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return "***";
        }
        return sessionId.substring(0, 8) + "***";
    }

    /**
     * Clears stored session credentials.
     */
    public static void clearCredentials() {
        sessionId = null;
        characterId = null;
        loginAttempted.set(false);
    }
}
```

---

### 步骤 3: 在 LoginManager 中添加 session-based 登录支持

**文件:** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/security/LoginManager.java`

**添加方法（约在 223 行之后）:**

```java
/**
 * Attempts a direct session-based login if session credentials are available.
 * This is called automatically when client detects --session-id and --character-id parameters.
 * 
 * @return true if session login was attempted, false otherwise
 */
public static boolean attemptDirectSessionLogin() {
    if (!DirectSessionLogin.hasSessionCredentials()) {
        return false;
    }

    if (isLoggedIn()) {
        log.debug("Already logged in, skipping direct session login");
        return false;
    }

    if (LOGIN_ATTEMPT_ACTIVE.get()) {
        log.debug("Login attempt already active, skipping direct session login");
        return false;
    }

    synchronized (LOGIN_LOCK) {
        LOGIN_ATTEMPT_ACTIVE.set(true);
    }

    try {
        return DirectSessionLogin.attemptLogin();
    } finally {
        // Session login is instant, no need to keep lock
        LOGIN_ATTEMPT_ACTIVE.set(false);
    }
}
```

---

### 步骤 4: 创建自动触发 session 登录的插件

**文件:** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/accountselector/SessionAutoLoginPlugin.java`

```java
package net.runelite.client.plugins.microbot.accountselector;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.DirectSessionLogin;
import net.runelite.client.plugins.microbot.util.security.LoginManager;

import javax.inject.Inject;

/**
 * Hidden, always-on plugin that automatically attempts session-based login
 * when --session-id and --character-id command line parameters are provided.
 */
@PluginDescriptor(
    name = "Session Auto Login",
    description = "Automatically logs in using session-id and character-id from command line",
    tags = {"microbot", "login", "session"},
    enabledByDefault = true,
    hidden = true
)
@Slf4j
public class SessionAutoLoginPlugin extends Plugin {

    @Inject
    private Client client;

    private boolean loginAttempted = false;

    @Override
    protected void startUp() {
        log.debug("SessionAutoLoginPlugin started");
        loginAttempted = false;
    }

    @Override
    protected void shutDown() {
        log.debug("SessionAutoLoginPlugin stopped");
        loginAttempted = false;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (loginAttempted) {
            return;
        }

        if (!DirectSessionLogin.hasSessionCredentials()) {
            // No session credentials provided, do nothing
            return;
        }

        GameState newState = event.getGameState();
        
        // Attempt login when we reach the login screen
        if (newState == GameState.LOGIN_SCREEN || newState == GameState.LOGIN_SCREEN_AUTHENTICATOR) {
            log.info("Login screen detected, attempting direct session login");
            
            // Small delay to ensure client is ready
            Microbot.getClientThread().invokeLater(() -> {
                try {
                    Thread.sleep(500);
                    boolean success = LoginManager.attemptDirectSessionLogin();
                    if (success) {
                        log.info("Direct session login initiated");
                        loginAttempted = true;
                    } else {
                        log.warn("Direct session login failed");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted during session login delay", e);
                }
            });
        } else if (newState == GameState.LOGGED_IN) {
            log.info("Successfully logged in via session");
            loginAttempted = true;
            LoginManager.markLoggedIn();
        }
    }

    @Provides
    AutoLoginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoLoginConfig.class);
    }
}
```

---

### 步骤 5: 在 RuneLite.java 启动流程中集成

**文件:** `runelite-client/src/main/java/net/runelite/client/RuneLite.java`

**位置:** 在创建 injector 之前（约 350 行附近）

```java
// 在解析参数之后，启动客户端之前
if (sessionId != null && characterId != null) {
    log.info("Session-based login parameters detected");
    DirectSessionLogin.setSessionCredentials(sessionId, characterId);
}
```

---

### 步骤 6: 更新文档

**文件:** `docs/CLIENT_LAUNCH.md`

在 "命令行参数（RuneLite 定义）" 部分添加：

```markdown
| `--session-id <ID>` | 直接使用 session ID 登录（跳过用户名/密码） | 必需参数 |
| `--character-id <ID>` | 指定要登录的角色 ID | 必需参数 |
```

在 "使用示例" 部分添加：

```markdown
### 示例 13：使用 Session ID 直接登录

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  --session-id "your-session-id-here" \
  --character-id "your-character-id-here"
```

或通过 Gradle：
```bash
./gradlew :client:run --args="--session-id your-session-id --character-id your-character-id"
```

**注意:** 这种方式跳过传统的用户名/密码认证，直接使用已有的会话令牌登录。
```

---

## 测试步骤

1. **获取有效的 session_id 和 character_id**
   - 先通过正常方式登录一次
   - 使用 `./microbot-cli state` 查看当前 session 信息
   - 或从浏览器开发者工具中抓取 Jagex 登录的 session token

2. **测试命令行启动**
```bash
java -jar client-1.0.0-SNAPSHOT-shaded.jar \
  --session-id "abc123def456..." \
  --character-id "12345"
```

3. **验证登录流程**
   - 检查日志中是否出现 "Direct session login initiated"
   - 确认客户端是否成功跳过用户名/密码输入
   - 验证是否直接进入游戏

4. **错误处理测试**
   - 测试无效的 session_id
   - 测试过期的 session_id
   - 测试只提供其中一个参数的情况

---

## 安全注意事项

1. **不要在日志中完整打印 session_id** - 已实现 `maskSessionId()` 方法
2. **session_id 应该通过环境变量或配置文件传递** - 避免在命令行历史中暴露
3. **定期刷新 session** - session_id 有过期时间
4. **不要提交包含真实 session_id 的代码或文档**

---

## 后续优化

1. **支持从环境变量读取 session credentials:**
```java
String sessionId = System.getenv("MICROBOT_SESSION_ID");
String characterId = System.getenv("MICROBOT_CHARACTER_ID");
```

2. **支持从配置文件读取:**
```java
// 从 ~/.runelite/session-credentials.properties 读取
```

3. **添加 session 刷新机制** - 当 session 即将过期时自动刷新

4. **支持多账号 session 切换** - 存储多个 session 并通过 character_id 选择

---

## 相关文件清单

- `runelite-client/src/main/java/net/runelite/client/RuneLite.java` - 添加命令行参数
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/security/DirectSessionLogin.java` - 新建
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/security/LoginManager.java` - 添加方法
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/accountselector/SessionAutoLoginPlugin.java` - 新建
- `docs/CLIENT_LAUNCH.md` - 更新文档
