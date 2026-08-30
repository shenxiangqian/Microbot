# 客户端启动参数完整指南

本文档列出了 Microbot 客户端启动时支持的所有命令行参数（通过 `RuneLite.java` 定义）和 JVM 系统属性。

## 目录
- [命令行参数（RuneLite 定义）](#命令行参数runelite-定义)
- [JVM 系统属性](#jvm-系统属性)
- [Gradle 任务](#gradle-任务)
- [使用示例](#使用示例)
- [常见问题](#常见问题)

---

## 命令行参数（RuneLite 定义）

这些参数通过 `--参数名` 或 `-参数名` 的形式传递给客户端 jar。

### 完整参数列表

| 参数 | 说明 | 参数值 |
|------|------|--------|
| `--help` | 显示帮助信息并退出 | 无 |
| `--developer-mode` | 启用开发者工具 | 无 |
| `--debug` | 启用调试日志输出（DEBUG 级别） | 无 |
| `--safe-mode` | 禁用外部插件和 GPU 插件（安全模式） | 无 |
| `--disable-telemetry` | 禁用遥测数据上传 | 无 |
| `--disable-walker-update` | 禁用静态 walker 更新 | 无 |
| `--insecure-skip-tls-verification` | 禁用 TLS 证书验证（不安全） | 无 |
| `--clean-jagex-launcher` | 删除 `credentials.properties` 文件，允许使用用户名/密码登录 | 无 |
| `--clean-randomdat` | 清理并重新创建当前 Jagex home 中的 `random.dat` 文件 | 无 |
| `--noupdate` | 跳过启动器更新检查 | 无 |
| `--insecure-write-credentials` | 将 Jagex Launcher 的认证 token 导出到文本文件（仅供开发） | 无 |
| `--jav_config <URL>` | 指定自定义 jav_config URL | 必需参数 |
| `--profile <名称>` | 使用指定的配置文件名称 | 必需参数 |
| `--proxy <URL>` | 使用指定的代理服务器 | 必需参数（格式：`scheme://user:pass@host:port`） |
| `--sessionfile <路径>` | 使用指定的会话文件 | 必需参数（默认：`~/.runelite/session`） |
| `--session-id <ID>` | 直接使用 session ID 登录（跳过用户名/密码） | 必需参数 |
| `--character-id <ID>` | 指定要登录的角色 ID | 必需参数 |
| `--accounts-root <路径>` | 按 character ID 隔离 Jagex 身份文件并共享游戏缓存 | 必需参数 |

### 使用方法

**显示帮助：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --help
```

**启用开发者模式：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --developer-mode
```

**启用调试日志：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --debug
```

**安全模式（禁用外部插件和 GPU）：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --safe-mode
```

**禁用遥测：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --disable-telemetry
```

**使用代理：**
```bash
# SOCKS5 代理（推荐）
java -jar client-<version>-SNAPSHOT-shaded.jar --proxy socks5://user:pass@proxy.example.com:1080

# 包含特殊字符的密码（自动编码，无需手动转码）
java -jar client-<version>-SNAPSHOT-shaded.jar --proxy "socks5://user:p@ss!w0rd#123@proxy.example.com:1080"
```

**注意：**
- 只支持 SOCKS5 代理（不支持 HTTP/HTTPS 代理）
- 密码中的特殊字符会自动进行 URL 编码，无需手动转换
- 例如：`!@#$%` 会自动编码为 `%21%40%23%24%25`

**使用指定的配置文件：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --profile myprofile
```

**清理 Jagex Launcher 凭据（允许用户名/密码登录）：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --clean-jagex-launcher
```

**自定义 jav_config URL：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --jav_config http://example.com/jav_config.ws
```

**使用 Session ID 直接登录（跳过用户名/密码）：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  --session-id "your-session-id-here" \
  --character-id "your-character-id-here"
```

或通过 Gradle：
```bash
./gradlew :client:run --args="--session-id your-session-id --character-id your-character-id"
```

**注意：**
- 这种方式跳过传统的用户名/密码认证，直接使用已有的会话令牌登录
- `--session-id` 和 `--character-id` 必须同时提供
- 凭据只通过临时文件传给 injected-client，客户端读取后立即删除
- Session ID 有过期时间，需要定期刷新
- **不要在命令行历史中暴露真实的 session ID**，建议使用环境变量或配置文件
- Session ID 和 Character ID 在启动日志中会完全脱敏

**按账号隔离 Jagex 身份文件并共享缓存：**
```powershell
java -jar Launcher.jar `
  --accounts-root "C:\MicrobotAccounts" `
  --session-id "your-session-id-here" `
  --character-id "344492934"
```

- 使用该参数时不要再传账号专用的 `-Duser.home`；RuneLite 设置、插件和脚本目录将继续共享
- `random.dat`、`preferences.dat` 和 `preferences2.dat` 保存在 `<accounts-root>\<character-id>\.runelite` 下并保持账号独立
- `main_file_cache.dat2` 和 `main_file_cache.idx*` 通过硬链接共享默认 `.runelite` 中的游戏缓存
- 公共 RuneLite 目录和 `accounts-root` 必须位于支持硬链接的同一磁盘分区

**组合使用多个参数：**
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  --developer-mode \
  --debug \
  --disable-telemetry
```

---

## JVM 系统属性

这些参数通过 `-D参数名=值` 的形式传递给 JVM。

### Microbot 专用属性

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `-Dmicrobot.disableTelemetry=true` | 禁用遥测（与 `--disable-telemetry` 相同） | `false` |
| `-Dmicrobot.test.mode=true` | 启用测试模式（自动化测试） | `false` |
| `-Dmicrobot.test.script=<名称>` | 测试模式下要自动启用的插件名称 | - |
| `-Dmicrobot.test.timeout=<毫秒>` | 测试超时时间（毫秒） | `120000` |
| `-Dmicrobot.test.output=<路径>` | 测试结果输出目录 | `~/.runelite/test-results` |
| `-Dmicrobot.test.webwalker.case=<名称>` | WebWalker 测试用例名称 | 运行全部 |
| `-Dmicrobot.test.webwalker.stopOnFailure=true` | WebWalker 首次失败后停止 | `false` |
| `-Dmicrobot.test.webwalker.walkTimeoutMs=<毫秒>` | WebWalker 单次路线超时 | `300000` |
| `-Dmicrobot.test.webwalker.useTeleportationSpells=true` | WebWalker 启用传送法术 | `true` |
| `-Dmicrobot.test.geLumbridge.iterations=<次数>` | GE-Lumbridge 循环次数 | `10` |
| `-Dmicrobot.test.geLumbridge.walkTimeoutMs=<毫秒>` | GE-Lumbridge 单次超时 | `180000` |
| `-Dmicrobot.bank.validateInventorySetup=true` | 启用银行库存预设验证 | `false` |
| `-Dmicrobot.scanner.enabled=true` | 启用客户端线程扫描器 | `false` |
| `-Dmicrobot.guardrail.regenerate-baseline=true` | 重新生成客户端线程 guardrail 基线 | `false` |
| `-Dmicrobot.queryable-guardrail.regenerate-baseline=true` | 重新生成 queryable terminal guardrail 基线 | `false` |

### 基础 JVM 属性

| 属性 | 说明 | 推荐值 |
|------|------|--------|
| `-Dfile.encoding=UTF-8` | 设置文件编码 | 必需 |
| `-ea` | 启用断言（assertions） | 开发/测试推荐 |
| `-Xmx<size>` | 最大堆内存 | `2G` 或更高 |
| `-Xms<size>` | 初始堆内存 | `512M` |

### RuneLite 原生属性

| 属性 | 说明 |
|------|------|
| `-Drunelite.rtconf=<URL>` | 自定义运行时配置 URL |
| `-Drunelite.http-service.url=<URL>` | HTTP 服务 URL |
| `-Drunelite.session.url=<URL>` | 会话服务 URL |
| `-Drunelite.static.url=<URL>` | 静态资源 URL |
| `-Drunelite.ws.url=<URL>` | WebSocket 服务 URL |
| `-Drunelite.pluginhub.url=<URL>` | Plugin Hub URL |
| `-Drunelite.launcher.version=<版本>` | Launcher 版本号 |
| `-Drunelite.useNativeBzip=true` | 启用原生 BZip2 库 |

### JDWP 调试属性

| 属性 | 说明 |
|------|------|
| `-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005` | 启用 JDWP 调试（端口 5005，启动时暂停） |
| `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005` | 启用 JDWP 调试（端口 5005，不暂停） |

### macOS 专用属性

```bash
--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED
--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED
--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED
--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED
```

---

## Gradle 任务

开发环境使用 Gradle 任务，自动配置 classpath 和参数。

### 应用任务

| 任务 | 说明 |
|------|------|
| `./gradlew :client:run` | 启动客户端（标准模式） |
| `./gradlew :client:runDebug` | 启动客户端（JDWP 调试，端口 5005） |
| `./gradlew :client:runTest` | 启动客户端（测试模式） |

### 构建任务

| 任务 | 说明 |
|------|------|
| `./gradlew :client:compileJava` | 快速编译 |
| `./gradlew :client:assemble` | 构建 shaded jar |
| `./gradlew buildAll` | 构建所有子项目 |
| `./gradlew cleanAll` | 清理所有构建输出 |

### 测试任务

| 任务 | 说明 |
|------|------|
| `./gradlew :client:runUnitTests` | 运行单元测试（CI 安全） |
| `./gradlew :client:runTests` | 运行所有测试 |
| `./gradlew :client:runIntegrationTest` | 运行集成测试（需要运行的客户端） |

### 向 Gradle 传递参数

**JVM 系统属性：**
```bash
./gradlew :client:run -Dmicrobot.disableTelemetry=true
```

**命令行参数（通过 `--args`）：**
```bash
./gradlew :client:run --args="--developer-mode --debug"
```

---

## 使用示例

### 示例 1：基本启动

```bash
java -jar client-1.0.0-SNAPSHOT-shaded.jar
```

### 示例 2：开发者模式 + 调试日志

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  --developer-mode \
  --debug
```

### 示例 3：禁用遥测 + 设置内存

```bash
java \
  -Dfile.encoding=UTF-8 \
  -Xmx2G \
  -Xms512M \
  -jar client-<version>-SNAPSHOT-shaded.jar \
  --disable-telemetry
```

### 示例 4：使用代理

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  --proxy http://username:password@proxy.example.com:8080
```

### 示例 5：安全模式（禁用外部插件）

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --safe-mode
```

### 示例 6：测试模式（自动化测试）

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  -Dmicrobot.test.mode=true \
  -Dmicrobot.test.script="Guard Killer Test" \
  -Dmicrobot.test.timeout=120000
```

或使用 Gradle：
```bash
./gradlew :client:runTest \
  -Dmicrobot.test.mode=true \
  -Dmicrobot.test.script="Guard Killer Test"
```

### 示例 7：JDWP 调试模式

```bash
java \
  -Dfile.encoding=UTF-8 \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
  -jar client-<version>-SNAPSHOT-shaded.jar
```

或使用 Gradle：
```bash
./gradlew :client:runDebug
```

### 示例 8：清理 Jagex Launcher 凭据

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --clean-jagex-launcher
```

### 示例 9：自定义配置文件

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --profile test-account
```

### 示例 10：完整的生产环境配置

```bash
java \
  -Dfile.encoding=UTF-8 \
  -Dmicrobot.disableTelemetry=true \
  -Xmx2G \
  -Xms512M \
  -ea \
  -jar client-1.0.0-SNAPSHOT-shaded.jar \
  --disable-telemetry \
  --profile main-account
```

### 示例 11：macOS 全屏支持

```bash
java \
  --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED \
  --add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED \
  --add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED \
  --add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED \
  -Dfile.encoding=UTF-8 \
  -jar client-<version>-SNAPSHOT-shaded.jar
```

或直接使用 Gradle（已自动包含）：
```bash
./gradlew :client:run
```

### 示例 12：WebWalker 测试

```bash
./gradlew :client:runTest \
  -Dmicrobot.test.mode=true \
  -Dmicrobot.test.script="F2P WebWalker Harness" \
  -Dmicrobot.test.webwalker.case="Lumbridge to Varrock" \
  -Dmicrobot.test.webwalker.stopOnFailure=true
```

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

---

## 常见问题

### Q1: 如何查看所有可用的命令行参数？

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar --help
```

### Q2: `--disable-telemetry` 和 `-Dmicrobot.disableTelemetry=true` 有什么区别？

两者功能相同，都禁用遥测。`--disable-telemetry` 是命令行参数，`-Dmicrobot.disableTelemetry=true` 是 JVM 系统属性。可以任选其一使用。

### Q3: 如何同时使用命令行参数和 JVM 属性？

```bash
java -Dfile.encoding=UTF-8 -Xmx2G \
  -jar client-<version>-SNAPSHOT-shaded.jar \
  --developer-mode --debug
```

JVM 属性（`-D` 和 `-X`）必须放在 `-jar` **之前**，命令行参数（`--`）放在 jar 文件名**之后**。

### Q4: 如何在 Gradle 中传递命令行参数？

```bash
./gradlew :client:run --args="--developer-mode --debug"
```

### Q5: `--developer-mode` 有什么作用？

启用开发者工具，并在未启用断言（`-ea`）时显示警告对话框。仅在非 launcher 环境下有效。

### Q6: `--safe-mode` 禁用哪些插件？

禁用所有外部插件（通过 Plugin Hub 安装的）和 GPU 插件。内置插件仍然可用。

### Q7: 如何使用指定的会话文件？

```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  --sessionfile /path/to/my-session-file
```

默认会话文件位置：`~/.runelite/session`

### Q8: `--clean-randomdat` 有什么用？

删除并重新创建当前 Jagex home 下的 `random.dat` 文件（只读）。使用 `--accounts-root` 时，该文件位于对应 character ID 的 `.runelite` 目录中。

### Q9: 如何验证代理是否正常工作？

启动时会显示检测到的外部 IP 地址：
```bash
java -jar client-<version>-SNAPSHOT-shaded.jar \
  --proxy http://user:pass@proxy.example.com:8080
```

如果代理配置正确，客户端 UI 会显示 "Proxy enabled (IP x.x.x.x)"。

### Q10: 如何查看客户端实际使用的 JVM 参数？

**方法 1：查看日志**
```bash
cat ~/.runelite/logs/client.log | grep "Java VM arguments"
```

**方法 2：使用 jps**
```bash
jps -v | grep RuneLite
```

---

## 参数速查表

### 常用命令

```bash
# 基本启动
java -jar client-<version>-SNAPSHOT-shaded.jar

# 开发者模式
java -jar client-<version>-SNAPSHOT-shaded.jar --developer-mode --debug

# 禁用遥测
java -jar client-<version>-SNAPSHOT-shaded.jar --disable-telemetry

# 安全模式
java -jar client-<version>-SNAPSHOT-shaded.jar --safe-mode

# 使用代理
java -jar client-<version>-SNAPSHOT-shaded.jar --proxy http://user:pass@host:port

# 调试模式
./gradlew :client:runDebug

# 测试模式
./gradlew :client:runTest -Dmicrobot.test.mode=true -Dmicrobot.test.script="MyPlugin"

# 完整生产配置
java -Dfile.encoding=UTF-8 -Xmx2G -jar client-<version>-SNAPSHOT-shaded.jar --disable-telemetry
```

---

## 相关文档

- **安装指南：** `docs/installation.md`
- **开发环境设置：** `docs/development.md`
- **CLI 工具使用：** `docs/MICROBOT_CLI.md`
- **Agent Server API：** `docs/AGENT_SERVER.md`
- **自动化测试循环：** `docs/AGENTIC_TESTING_LOOP.md`
- **脚本开发规则：** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/AGENTS.md`
- **架构文档：** `docs/ARCHITECTURE.md`
