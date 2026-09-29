# Builds the app as ONE self-contained Windows application:
#   dist-app\ATM\ATM.exe  (includes the Java runtime, the JDBC driver,
#                          JDateChooser and the images - nothing else needed)
#
#   .\package-app.ps1            normal build
#   .\package-app.ps1 -Installer also creates a setup.exe (needs WiX v3 toolset)
param(
    [switch]$Installer,
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    $jpackage = (Get-Command jpackage -ErrorAction SilentlyContinue).Source
}
if (-not $jpackage) { throw "jpackage.exe not found. Install a JDK 17+ (with jpackage) or set JAVA_HOME." }

Write-Host "==> 1/2 building target\atm-system.jar"
& mvn -B -q package
if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

Write-Host "==> 2/2 running jpackage"
if (Test-Path "dist-app") { Remove-Item -Recurse -Force "dist-app" }

$type = if ($Installer) { "msi" } else { "app-image" }
$jargs = @(
    "--type", $type,
    "--name", "ATM",
    "--input", "target",
    "--main-jar", "atm-system.jar",
    "--main-class", "atm.system.AtmSystem",
    "--app-version", $Version,
    "--vendor", "ATM System",
    "--dest", "dist-app"
)
if ($Installer) {
    $jargs += @("--win-dir-chooser", "--win-menu", "--win-shortcut")
}

& $jpackage @jargs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Write-Host ""
Write-Host "Done. Run it with:  dist-app\ATM\ATM.exe"
Write-Host "Database settings: atm-db.properties (see atm-db.properties.example)"
