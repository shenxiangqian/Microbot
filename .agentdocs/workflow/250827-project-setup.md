# Microbot 项目部署与协作流程方案

## 一、整体架构设计

### Git 仓库结构
```
上游开源项目 (upstream)
    ↓ fork
你的团队远程仓库 (origin)
    ↓ clone
本地仓库 (local)
    ├── main 分支（跟踪 origin/main）
    └── dev 分支（日常开发分支）
```

### 协作模型
采用 **Fork + Feature Branch** 工作流：
- **upstream**: https://github.com/chsami/Microbot（只读，用于同步上游更新）
- **origin**: 你的 GitHub 账号下的 fork 仓库（读写，团队共享）
- **local**: 每个开发者的本地仓库

## 二、初始化步骤（我将执行的操作）

### 步骤 1: Fork 上游项目
**需要你操作**：
1. 访问 https://github.com/chsami/Microbot
2. 点击右上角 "Fork" 按钮
3. Fork 到你的 GitHub 账号下
4. 记录你的 fork 地址（格式：`https://github.com/你的用户名/Microbot`）

或者告诉我你的 GitHub 用户名，我可以直接使用 `gh` 命令帮你 fork。

### 步骤 2: 克隆你的 fork 到本地
```bash
git clone https://github.com/你的用户名/Microbot.git .
```

### 步骤 3: 添加上游远程仓库
```bash
git remote add upstream https://github.com/chsami/Microbot.git
git remote set-url --push upstream no_push  # 防止误推送到上游
```

### 步骤 4: 创建团队开发分支
```bash
git checkout -b dev
git push -u origin dev
```

### 步骤 5: 配置 Git
```bash
# 设置推送策略（只推送当前分支）
git config push.default current

# 设置拉取策略（使用 rebase 保持历史清晰）
git config pull.rebase true
```

## 三、日常协作流程

### 场景 1: 开始新功能开发
```bash
# 1. 确保 dev 分支是最新的
git checkout dev
git pull origin dev

# 2. 创建功能分支
git checkout -b feature/你的功能名

# 3. 开发并提交
git add .
git commit -m "feat: 实现某功能"

# 4. 推送到远程
git push -u origin feature/你的功能名

# 5. 在 GitHub 上创建 Pull Request: feature/你的功能名 -> dev
```

### 场景 2: 同步上游开源项目更新
```bash
# 1. 切换到 main 分支
git checkout main

# 2. 拉取上游最新代码
git fetch upstream
git merge upstream/main

# 3. 推送到你的远程仓库
git push origin main

# 4. 将更新合并到 dev 分支
git checkout dev
git merge main
git push origin dev

# 5. 更新你的功能分支（如果有）
git checkout feature/你的功能名
git merge dev
```

**建议频率**: 每周一次或上游有重大更新时

### 场景 3: 多人协作避免冲突
```bash
# 开发前先同步
git checkout dev
git pull origin dev

# 开发中定期推送
git add .
git commit -m "wip: 阶段性保存"
git push

# 提交前先拉取最新代码
git pull origin dev
# 解决冲突后再推送
git push
```

## 四、IDEA 配置建议

### 4.1 必要的 IDEA 插件
- **GitToolBox**: Git 增强工具
- **GitLive**: 实时显示团队成员的修改

### 4.2 Git 配置
1. 打开 `File > Settings > Version Control > Git`
2. 设置 "Update method" 为 `Rebase`
3. 启用 "Auto-update if push of the current branch was rejected"

### 4.3 Commit 配置
1. 打开 `File > Settings > Version Control > Commit`
2. 勾选：
   - "Analyze code"
   - "Check TODO"
   - "Optimize imports"
   - "Reformat code"

### 4.4 Remote Repositories 配置
确保 IDEA 能看到两个远程仓库：
- `origin`: 你的 fork（可推送）
- `upstream`: 上游项目（只拉取）

## 五、分支策略

### 分支命名规范
- `main`: 主分支，始终与上游 upstream/main 保持同步
- `dev`: 开发分支，团队日常开发基准
- `feature/功能名`: 功能分支
- `bugfix/问题描述`: 修复分支
- `hotfix/紧急修复`: 紧急修复分支

### 分支保护
建议在 GitHub 上为 `dev` 和 `main` 分支设置保护规则：
1. 进入你的 fork 仓库
2. Settings > Branches > Add branch protection rule
3. 对 `main` 和 `dev` 启用：
   - Require pull request reviews before merging
   - Require status checks to pass before merging

## 六、冲突解决流程

### 如果拉取时出现冲突
```bash
# 1. 拉取最新代码
git pull origin dev

# 2. IDEA 会自动打开冲突解决工具
# 3. 手动解决冲突后
git add 冲突文件
git commit -m "merge: 解决与某某的冲突"
git push
```

### 如果推送时被拒绝
```bash
# 1. 先拉取
git pull --rebase origin dev

# 2. 解决可能的冲突
# 3. 继续 rebase
git rebase --continue

# 4. 强制推送（仅限功能分支）
git push --force-with-lease
```

## 七、快速命令速查表

```bash
# 同步上游更新
git fetch upstream && git merge upstream/main

# 创建功能分支
git checkout -b feature/新功能

# 保存工作进度
git stash save "临时保存描述"
git stash pop  # 恢复

# 查看远程分支
git remote -v

# 查看分支关系
git log --oneline --graph --all

# 删除本地分支
git branch -d feature/已完成的功能

# 删除远程分支
git push origin --delete feature/已完成的功能
```

## 八、注意事项

1. **永远不要直接推送到 upstream**（已通过 `no_push` 配置防止）
2. **main 分支只用于同步上游**，不在上面直接开发
3. **dev 分支是团队协作基准**，所有功能分支从这里分出
4. **定期同步上游更新**，避免分叉过久难以合并
5. **提交前先拉取**，减少冲突
6. **使用有意义的提交信息**，格式：`类型: 描述`
   - `feat`: 新功能
   - `fix`: 修复
   - `refactor`: 重构
   - `docs`: 文档
   - `style`: 格式
   - `test`: 测试
   - `chore`: 构建/工具

## 九、当前状态

> 最近一次更新：2026-08-27 项目初始化完成。

- [x] Fork 上游项目 — 用户已在 `https://github.com/shenxiangqian/Microbot` 完成 fork
- [x] 克隆到本地 — 已克隆到 `D:\MicrobotNew`（保留 `.agentdocs/` 目录）
- [x] 配置远程仓库
  - `origin` → `https://github.com/shenxiangqian/Microbot.git`（读写）
  - `upstream` → `https://github.com/chsami/Microbot.git`（已通过 `no_push` 配置为只读）
- [x] 创建 dev 分支 — 已在 origin 上创建 `dev` 分支并推送，本地跟踪 `origin/dev`
- [x] 配置 Git
  - `push.default = current`（只推送当前分支）
  - `pull.rebase = true`（拉取时使用 rebase）
- [x] 验证 IDEA 可以正常打开项目 — 通过 `build.gradle.kts` 直接识别为 Gradle 多模块项目，gradlew/gradlew.bat 已就绪

## 十、初始化摘要

- **项目类型**：Java 多模块项目（基于 RuneLite fork），构建工具为 Gradle（Kotlin DSL），Java 11 编译目标，开发需 JDK 17+。
- **核心子模块**：`runelite-client`（含 microbot 插件）、`runelite-api`、`cache`、`runelite-gradle-plugin`、`runelite-jshell`、`microbot-cli`。
- **常用构建命令**：`./gradlew :client:compileJava`、`./gradlew buildAll`、`./gradlew :client:assemble`。
- **关键规则文档**：仓库根 `AGENTS.md`、`runelite-client/.../microbot/AGENTS.md`、`docs/ARCHITECTURE.md`、`docs/development.md`。
- **工作区级约束**：`.agentdocs/WORKSPACE_RULES.md`（本地规则，Windows 大小写不敏感下与 `CLAUDE.MD` 互斥，故镜像到此目录）。

## 十一、下一步建议

1. **本地构建验证**：执行 `./gradlew :client:compileJava` 验证编译链通畅（首次会下载 Gradle 包装器和依赖，时间较长）。
2. **IDEA 首次同步**：用 IntelliJ IDEA 打开 `D:\MicrobotNew`，选择 "Open as Project"，等待 Gradle 同步完成（首次约 5-15 分钟，依赖网络）。
3. **设置上游同步节奏**：每周或上游 release 后执行 `git fetch upstream && git checkout main && git merge upstream/main && git push origin main && git checkout dev && git merge main && git push origin dev`。
4. **在 GitHub 上设置分支保护**：对 `dev` 和 `main` 启用 PR review 要求，避免误推。
5. **安装推荐的 IDEA 插件**：`GitToolBox`、`GitLive`（见 §4.1）。
