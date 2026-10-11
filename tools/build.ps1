param([switch]$Install, [string]$Serial = 'emulator-5556')
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    if (-not $env:JAVA_HOME) {
        $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
        if (Test-Path -LiteralPath (Join-Path $studioJdk 'bin\java.exe')) { $env:JAVA_HOME = $studioJdk }
        else { throw 'Install JDK 17+ or set JAVA_HOME to your JDK.' }
    }
    $androidSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    if (-not (Test-Path -LiteralPath 'local.properties')) {
        if (-not (Test-Path -LiteralPath $androidSdk)) { throw 'Android SDK not found. Set ANDROID_HOME or create local.properties.' }
        $escapedSdk = $androidSdk.Replace('\', '/').Replace(':', '\:')
        Set-Content -LiteralPath 'local.properties' -Value "sdk.dir=$escapedSdk" -Encoding utf8
    }
    & .\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or verification failed.' }
    New-Item -ItemType Directory -Force -Path 'artifacts' | Out-Null
    $apkFile = Join-Path $projectDirectory 'artifacts\yaya-home-v0.1.0.apk'
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination $apkFile -Force
    if ($Install) {
        $adbExe = Join-Path $androidSdk 'platform-tools\adb.exe'
        & $adbExe -s $Serial install -r $apkFile
        if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
        & $adbExe -s $Serial shell am start -n 'com.string.aipet/.MainActivity'
        if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
    }
    Write-Output "APK: $apkFile"
} finally { Pop-Location }
