# Runs Gradle with a JDK 25, finding one itself.
#
# Minecraft 26.2 needs Java 25, and this machine has no JDK on PATH and no JAVA_HOME —
# which is why plain `gradlew build` fails with "JAVA_HOME is not set". Rather than
# changing a system setting, this script locates a JDK 25 for the length of one build.
#
#   .\build.ps1              -> gradlew build
#   .\build.ps1 runClient    -> gradlew runClient
#   .\build.ps1 clean build  -> any Gradle arguments pass straight through
#
# To stop needing it, install a JDK 25 (Temurin or Azul both work) and set JAVA_HOME
# permanently:  setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-25"

$ErrorActionPreference = 'Stop'
$MinimumJava = 25

# Major version of the JDK installed at $Home, or 0 if there isn't a usable one there.
#
# Read from the `release` file every JDK ships rather than by running `java -version`:
# it avoids spawning a process per candidate, and PowerShell turns a native command's
# stderr into terminating errors under ErrorActionPreference=Stop, which is exactly where
# `java -version` writes. The `cmd /c` fallback keeps that redirect out of PowerShell.
function Get-JavaMajor {
    param([string]$JdkHome)

    if (-not $JdkHome) { return 0 }
    $javaExe = Join-Path $JdkHome 'bin\java.exe'
    if (-not (Test-Path $javaExe)) { return 0 }

    $releaseFile = Join-Path $JdkHome 'release'
    if (Test-Path $releaseFile) {
        $line = Select-String -Path $releaseFile -Pattern '^JAVA_VERSION="?(\d+)' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($line) { return [int]$line.Matches[0].Groups[1].Value }
    }

    $output = cmd /c "`"$javaExe`" -version 2>&1"
    if ($output -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

function Find-Jdk {
    # Whatever is already configured wins, so this never overrides a deliberate choice.
    if ($env:JAVA_HOME -and (Get-JavaMajor $env:JAVA_HOME) -ge $MinimumJava) {
        return $env:JAVA_HOME
    }

    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) {
        $home = Split-Path (Split-Path $onPath.Source -Parent) -Parent
        if ((Get-JavaMajor $home) -ge $MinimumJava) { return $home }
    }

    # Common install roots, plus the runtimes IDEs and launchers ship with. Each pattern
    # is a directory that should directly contain bin\java.exe.
    $patterns = @(
        'C:\Program Files\Java\*',
        'C:\Program Files\Eclipse Adoptium\*',
        'C:\Program Files\Zulu\*',
        'C:\Program Files\Microsoft\jdk*',
        'C:\Program Files\Amazon Corretto\*',
        'C:\Program Files\JetBrains\*\jbr',
        'C:\Program Files\Android\Android Studio\jbr',
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\*",
        "$env:APPDATA\ModrinthApp\java\*",
        "$env:APPDATA\.minecraft\runtime\*\windows-x64\*"
    )

    $best = $null
    $bestVersion = 0
    foreach ($pattern in $patterns) {
        foreach ($match in (Resolve-Path $pattern -ErrorAction SilentlyContinue)) {
            $version = Get-JavaMajor $match.Path
            if ($version -ge $MinimumJava -and $version -gt $bestVersion) {
                $best = $match.Path
                $bestVersion = $version
            }
        }
    }
    return $best
}

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Host ""
    Write-Host "No JDK $MinimumJava or newer found." -ForegroundColor Red
    Write-Host "Minecraft 26.2 mods need one. Install Temurin 25 from https://adoptium.net"
    Write-Host "and either re-run this script or set JAVA_HOME yourself."
    exit 1
}

Write-Host "Using JDK: $jdk" -ForegroundColor DarkGray

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"

# Wrapped in @() deliberately: a one-element $args unwraps to a bare string, and splatting
# a string spreads its characters, so `build.cmd build` would ask Gradle for tasks b, u,
# i, l and d.
$gradleArgs = @(if ($args.Count -gt 0) { $args } else { 'build' })
& (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArgs
exit $LASTEXITCODE
