# Session-based Login Test Script
# Usage: .\test-session-login.ps1 -SessionId "your-session-id" -CharacterId "your-character-id"

param(
    [Parameter(Mandatory=$true)]
    [string]$SessionId,
    
    [Parameter(Mandatory=$true)]
    [string]$CharacterId,
    
    [string]$JarPath = "runelite-client\build\libs\client-1.0.0-SNAPSHOT-shaded.jar"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Session-based Login Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if JAR exists
if (-not (Test-Path $JarPath)) {
    Write-Host "Error: JAR file not found at: $JarPath" -ForegroundColor Red
    Write-Host "Building the client first..." -ForegroundColor Yellow
    & .\gradlew.bat :client:assemble
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed!" -ForegroundColor Red
        exit 1
    }
}

# Mask session ID for display (show first 8 chars only)
$maskedSessionId = $SessionId.Substring(0, [Math]::Min(8, $SessionId.Length)) + "***"

Write-Host "Configuration:" -ForegroundColor Green
Write-Host "  Session ID: $maskedSessionId"
Write-Host "  Character ID: $CharacterId"
Write-Host "  JAR Path: $JarPath"
Write-Host ""

Write-Host "Starting client with session login..." -ForegroundColor Yellow
Write-Host ""

# Launch the client
& java -jar $JarPath --session-id $SessionId --character-id $CharacterId --debug

Write-Host ""
Write-Host "Client exited." -ForegroundColor Cyan
