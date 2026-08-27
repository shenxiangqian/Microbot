# Microbot Deploy Script - Windows PowerShell
# Usage: .\deploy.ps1 [OPTIONS]
# Compatible: Windows 10/11, PowerShell 5+

param(
    [string]$ForkRepo = "https://github.com/shenxiangqian/Microbot",
    [string]$UpstreamRepo = "https://github.com/chsami/Microbot",
    [string]$Branch = "dev",
    [string]$TargetDir = "",
    [switch]$SkipJavaCheck,
    [switch]$SkipGitProxy,
    [switch]$UseGradleMirror,
    [switch]$SkipClone,
    [switch]$Help
)

# ============================================================
# Defaults - branch names for repos
# ============================================================

# GitHub repos migrated to 'main' as default branch in late 2020.
# Specify 'master' only if you know the repo specifically uses it.
$DefaultForkBranch = "main"
$DefaultUpstreamBranch = "main"

# Gradle mirror configuration for China/Mirror networks
$GradleMirrorConfig = @"
org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Xmx2048m
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon=true

pluginManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        maven { url 'https://repo.maven.apache.org/maven2' }
        maven { url 'https://plugins.gradle.org/m2/' }
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/jcenter' }
        maven { url 'https://repo.maven.apache.org/maven2' }
    }
}
"@

# Git proxy settings (optional)
$GitProxyHost = ""
$GitProxyPort = ""

# ============================================================
# Utility Functions
# ============================================================

function Write-Success { param($msg) Write-Host "[OK]     $msg" -ForegroundColor Green }
function Write-Info { param($msg) Write-Host "[INFO]   $msg" -ForegroundColor Cyan }
function Write-Warn { param($msg) Write-Host "[WARN]   $msg" -ForegroundColor Yellow }
function Write-Err { param($msg) Write-Host "[ERROR]  $msg" -ForegroundColor Red }
function Write-Step { param($msg) Write-Host "`n==== $msg ====" -ForegroundColor Magenta }

# ============================================================
# Entry Point
# ============================================================

if ($Help) {
    Write-Host @"
Microbot Deployment Script
======================

Usage: .\deploy.ps1 [OPTIONS]

Options:
  -ForkRepo <URL>       Fork repository URL (default: https://github.com/shenxiangqian/Microbot)
  -UpstreamRepo <URL>   Upstream repository URL (default: https://github.com/chsami/Microbot)
  -Branch <NAME>        Branch to checkout after clone (default: dev)
  -TargetDir <PATH>     Target directory (default: <script_dir>/Microbot)
  -SkipJavaCheck       Skip Java installation check
  -SkipGitProxy        Skip Git proxy configuration
  -UseGradleMirror     Use Aliyun Maven mirror for Gradle
  -SkipClone          Skip git clone (assume repo already exists)
  -Help                Show this help

Examples:
  # Standard fresh clone
  .\deploy.ps1

  # Use Gradle mirror (China network)
  .\deploy.ps1 -UseGradleMirror

  # Custom fork
  .\deploy.ps1 -ForkRepo https://github.com/yourname/Microbot -Branch main

"@ -ForegroundColor White
    exit 0
}

# ============================================================
# Step 0: Environment Checks
# ============================================================

$ErrorActionPreference = "Continue"
$ScriptDir = $PSScriptRoot
if (-not $TargetDir) {
    $TargetDir = Join-Path $ScriptDir "Microbot"
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor White
Write-Host "  Microbot Deployment Script (Windows)" -ForegroundColor White
Write-Host "===============================================" -ForegroundColor White
Write-Host ""
Write-Info "Target directory: $TargetDir"
Write-Info "Fork repository: $ForkRepo"
Write-Info "Upstream: $UpstreamRepo"
Write-Info "Branch: $Branch"
Write-Host ""

# ============================================================
# Step 1: Check Administrator Privileges
# ============================================================

Write-Step "Step 1: Check Administrator Privileges"

$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Warn "Not running as administrator. Some operations may require elevation."
}

# ============================================================
# Step 2: Check PowerShell Version
# ============================================================

Write-Step "Step 2: Check PowerShell"

if ($PSVersionTable.PSVersion.Major -lt 5) {
    Write-Err "PowerShell 5.0 or higher required. Current: $($PSVersionTable.PSVersion)"
    exit 1
}
Write-Success "PowerShell version: $($PSVersionTable.PSVersion)"

# ============================================================
# Step 3: Check or Install Git
# ============================================================

Write-Step "Step 3: Check Git"

function Install-Git {
    Write-Info "Git not found. Attempting silent install..."
    $gitPath = Get-Command git -ErrorAction SilentlyContinue
    if ($gitPath) {
        $gitVersion = & git --version 2>&1
        Write-Success "Git installed: $gitVersion"
        return $true
    }

    Write-Warn "Git is not installed."
    Write-Info "Download Git for Windows from:"
    Write-Info "  https://git-scm.com/download/win"
    Write-Info "Install silently with: /VERYSILENT /NORESTART"

    $installChoice = Read-Host "Download and install Git for Windows? (y/n)"
    if ($installChoice -eq 'y') {
        try {
            Write-Info "Downloading Git installer..."
            $gitInstaller = "$env:TEMP\Git-2.44.0-64-bit.exe"
            Invoke-WebRequest -Uri "https://github.com/git-for-windows/git/releases/download/v2.44.0.windows.1/Git-2.44.0-64-bit.exe" -OutFile $gitInstaller -TimeoutSec 60
            Write-Success "Installer saved to: $gitInstaller"
            Write-Info "Run the installer to complete Git setup."
            Start-Process $gitInstaller
            return $false
        } catch {
            Write-Err "Download failed: $_"
            return $false
        }
    }
    return $false
}

if (-not (Install-Git)) {
    Write-Err "Git installation not found or not completed. Please install Git manually and re-run."
    exit 1
}

# Configure Git global settings
Write-Info "Configuring Git global settings..."
git config --global core.autocrlf true 2>$null
git config --global core.filemode false 2>$null
git config --global pull.rebase true
git config --global push.default current
git config --global init.defaultBranch master
Write-Success "Git global settings configured."

# ============================================================
# Step 4: Clone or Update Repository
# ============================================================

Write-Step "Step 4: Clone or Update Repository"

if (Test-Path (Join-Path $TargetDir ".git")) {
    Write-Info "Repository already exists at $TargetDir. Updating..."
    Set-Location $TargetDir

    # Verify fork remote
    Write-Info "Fetching fork remote..."
    $fetchOutput = git fetch origin 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "fetch failed (exit $LASTEXITCODE): $fetchOutput"
    } else {
        Write-Success "Fork remote verified."
    }
} else {
    if ($SkipClone) {
        Write-Err "Target directory $TargetDir is not a git repository and -SkipClone was specified. Cannot proceed."
        exit 1
    }

    Write-Info "Cloning repository: $ForkRepo"
    Write-Info "Target directory: $TargetDir"

    # Create parent directory if needed
    $parentDir = Split-Path $TargetDir -Parent
    if (-not (Test-Path $parentDir)) {
        New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
    }

    # Check if target directory exists but is not a git repo (e.g., empty or stale)
    if (Test-Path $TargetDir) {
        $existingItems = Get-ChildItem -Force $TargetDir -ErrorAction SilentlyContinue
        if ($existingItems -and $existingItems.Count -gt 0) {
            $count = ($existingItems | Measure-Object).Count
            Write-Err "Target directory $TargetDir is not empty ($count item(s) present)."
            Write-Err "Remove or rename it before running this script."
            exit 1
        } else {
            Remove-Item $TargetDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    # NOTE: We do NOT use --branch when cloning. Git automatically clones the
    # repository's default branch (main on GitHub since ~Oct 2020). Specifying
    # --branch master on a main-branch repository causes: "fatal: Remote branch
    # master not found in upstream origin" (exit 128).
    # If you need a specific branch, set $DefaultForkBranch in this script.
    Write-Info "Cloning ($ForkRepo) -> $TargetDir"

    $cloneCmd = "git clone"
    if ($DefaultForkBranch -and $DefaultForkBranch.Trim()) {
        $cloneCmd = "git clone --branch `"$DefaultForkBranch`""
        Write-Info "Using branch: $DefaultForkBranch"
    } else {
        Write-Info "Using repository default branch"
    }

    # Build and execute clone command, capturing exit code properly.
    # Using Start-Process would hide stderr, so we use Invoke-Expression
    # with explicit exit-code capture. Direct string exec via & avoids the
    # splatting/named-param pitfall with --branch.
    $cloneFullCmd = "$cloneCmd `"$ForkRepo`" `"$TargetDir`""
    $global:LASTEXITCODE = 0
    $cloneOutput = Invoke-Expression "$cloneFullCmd 2>&1 | Out-String"
    $cloneExit = $LASTEXITCODE

    if ($cloneExit -ne 0) {
        Write-Err "Git clone failed (exit $cloneExit)"
        Write-Host ""
        Write-Host "----- Git Error Output -----" -ForegroundColor Red
        if ($cloneOutput) {
            $cloneOutput -split "`n" | Select-Object -Last 30 | ForEach-Object { Write-Host "  $_" }
        } else {
            Write-Host "  (no output captured)"
        }
        Write-Host "----------------------------" -ForegroundColor Red
        Write-Host ""
        Write-Info "Troubleshooting:"
        Write-Info "  1) Verify Git is working: git ls-remote $ForkRepo HEAD"
        Write-Info "  2) Check network/firewall/proxy settings"
        Write-Info "  3) Try specifying -Branch option if repository uses a different default branch"
        exit 1
    }

    # Sanity check: ensure .git directory was created
    if (-not (Test-Path (Join-Path $TargetDir ".git"))) {
        Write-Err "Git clone completed but .git directory not found. Clone may have been corrupted."
        exit 1
    }

    Set-Location $TargetDir
    Write-Success "Repository cloned successfully ($DefaultForkBranch branch)."
}

# ============================================================
# Step 5: Configure Git Remotes
# ============================================================

Write-Step "Step 5: Configure Git Remotes"

# Check and add/update upstream remote
$remotes = git remote -v 2>$null
if ($remotes -match "upstream") {
    Write-Info "upstream remote already exists. Updating URL..."
    git remote set-url upstream $UpstreamRepo 2>$null
} else {
    Write-Info "Adding upstream remote: $UpstreamRepo"
    git remote add upstream $UpstreamRepo 2>$null
}

# Disable push for upstream (read-only)
git remote set-url --push upstream no_push 2>$null
Write-Success "upstream remote configured (push disabled)."

# Show all remotes
Write-Info "Current remotes:"
git remote -v
Write-Host ""

# ============================================================
# Step 6: Configure Git Proxy (Optional)
# ============================================================

if (-not $SkipGitProxy) {
    Write-Step "Step 6: Git Proxy Configuration"

    if ($GitProxyHost -and $GitProxyPort) {
        Write-Info "Configuring Git proxy: http://${GitProxyHost}:${GitProxyPort}"
        git config --global http.proxy "http://${GitProxyHost}:${GitProxyPort}"
        git config --global https.proxy "http://${GitProxyHost}:${GitProxyPort}"
        Write-Success "Git proxy configured."
    } else {
        Write-Info "Skipping proxy configuration (set `$GitProxyHost and `$GitProxyPort in script if needed)."
    }
}

# ============================================================
# Step 7: Switch to Target Branch
# ============================================================

Write-Step "Step 7: Switch to Target Branch"

$currentBranch = git branch --show-current
if ($currentBranch -eq $Branch) {
    Write-Info "Already on branch: $Branch"
} elseif (git branch -a | Select-String -Pattern "^\*\s*$Branch") {
    Write-Info "Switching to existing branch: $Branch"
    git checkout $Branch
} else {
    Write-Info "Creating and switching to new branch: $Branch"
    git checkout -b $Branch
}
Write-Success "Current branch: $(git branch --show-current)"

# ============================================================
# Step 8: Sync with Upstream (Optional)
# ============================================================

Write-Step "Step 8: Sync with Upstream"

$syncChoice = Read-Host "Sync with upstream repository? (y/n)"
if ($syncChoice -eq 'y') {
    Write-Info "Fetching upstream..."
    git fetch upstream 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "fetch upstream failed (exit $LASTEXITCODE). Network or proxy issue."
    }
    Write-Info "Merging upstream/$DefaultUpstreamBranch..."
    $global:LASTEXITCODE = 0
    & git merge "upstream/$DefaultUpstreamBranch" --no-edit 2>&1 | Tee-Object -Variable mergeOutput | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Merge conflicts detected. Resolve manually and commit."
        if ($mergeOutput) {
            Write-Host "----- merge output -----" -ForegroundColor Yellow
            $mergeOutput | Select-Object -Last 15 | ForEach-Object { Write-Host "  $_" }
            Write-Host "----------------------" -ForegroundColor Yellow
        }
    } else {
        Write-Success "Upstream sync completed successfully."
    }
}

# ============================================================
# Step 9: Configure Gradle Mirror (Optional)
# ============================================================

if ($UseGradleMirror) {
    Write-Step "Step 9: Configure Gradle Mirror"

    $gradlePropsFile = Join-Path $TargetDir "gradle.properties"
    $settingsFile = Join-Path $TargetDir "settings.gradle.kts"

    # Backup existing settings
    if (Test-Path $settingsFile) {
        Copy-Item $settingsFile "$settingsFile.bak" -Force
        Write-Info "Backed up settings.gradle.kts"
    }

    # Prepend mirror header to settings.gradle.kts
    $mirrorHeader = @"
// ==================== GRADLE MIRROR HEADER =====================
// This file was modified by deploy.ps1 to use mirror repositories.
// ============================================================
pluginManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        maven { url 'https://repo.maven.apache.org/maven2' }
        maven { url 'https://plugins.gradle.org/m2/' }
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/jcenter' }
        maven { url 'https://repo.maven.apache.org/maven2' }
    }
}
// ============================================================

"@

    $originalContent = Get-Content $settingsFile -Raw -ErrorAction SilentlyContinue
    if ($originalContent) {
        # Only add if mirror is not already present
        if ($originalContent -notmatch "maven.aliyun.com") {
            $newContent = $mirrorHeader + $originalContent
            Set-Content -Path $settingsFile -Value $newContent -NoNewline
            Write-Success "settings.gradle.kts mirror header added."
        } else {
            Write-Info "Gradle mirror already configured in settings.gradle.kts."
        }
    }

    # Update gradle.properties
    $propsContent = Get-Content $gradlePropsFile -Raw -ErrorAction SilentlyContinue
    if ($propsContent -notmatch "maven.aliyun.com") {
        $propsContent += "`n`n# Gradle mirror configuration`nmaven.aliyun.mirror=https://maven.aliyun.com/repository/public`n"
        Set-Content -Path $gradlePropsFile -Value $propsContent -NoNewline
        Write-Success "gradle.properties mirror config added."
    }
}

# ============================================================
# Step 10: Verify Gradle
# ============================================================

Write-Step "Step 10: Verify Gradle"

Write-Info "Checking Gradle wrapper..."
Write-Info "This may take a few minutes on first run..."

$gradlew = Join-Path $TargetDir "gradlew.bat"
if (Test-Path $gradlew) {
    Write-Info "Running Gradle wrapper verification..."

    try {
        & .\gradlew.bat tasks --quiet 2>&1 | Select-Object -First 20
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Gradle wrapper is working."
        } else {
            Write-Warn "Gradle wrapper exited with code $LASTEXITCODE. Check network/proxy settings."
        }
    } catch {
        Write-Warn "Gradle verification error: $_"
        Write-Info "To troubleshoot manually run: .\gradlew.bat tasks"
    }
} else {
    Write-Warn "gradlew.bat not found. Run: .\gradlew.bat tasks"
}

# ============================================================
# Step 11: Compilation Test (Optional)
# ============================================================

Write-Step "Step 11: Compilation Test (Optional)"

$verifyChoice = Read-Host "Run compilation test? (y/n)"
if ($verifyChoice -eq 'y') {
    Write-Info "Running: .\gradlew.bat :client:compileJava"
    Write-Info "This may take several minutes..."

    $startTime = Get-Date
    & .\gradlew.bat :client:compileJava --console=plain 2>&1 | Select-Object -Last 30
    $elapsed = (Get-Date) - $startTime

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Compilation successful! Elapsed: $($elapsed.ToString('mm\:ss'))"
    } else {
        Write-Warn "Compilation failed. Check the output above for errors."
    }
}

# ============================================================
# Summary
# ============================================================

Write-Step "Deployment Complete"

Write-Host @"

===============================================
  Summary and Next Steps
===============================================

1. Open in IntelliJ IDEA
   - Open: $TargetDir\build.gradle.kts
   - Configure: File -> Project Structure -> Project
   - Set: Project SDK = JDK 17+

2. Useful commands:
   - Compile: .\gradlew.bat :client:compileJava
   - Run:     .\gradlew.bat :client:run
   - Build:   .\gradlew.bat :client:assemble

3. Git workflow:
   - Check status: git status
   - Pull updates: git pull
   - Commit changes: git add . && git commit -m 'message' && git push

4. Sync with upstream:
   - git fetch upstream
   - git merge upstream/main

===============================================

"@ -ForegroundColor Green

# Return to original directory
Write-Info "Return to directory: $ScriptDir"
