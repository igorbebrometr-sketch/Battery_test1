@echo off
setlocal

if exist "%~dp0build.local.bat" call "%~dp0build.local.bat"

if not defined JAVA_HOME (
    echo ERROR: Set JAVA_HOME to a JDK 17 installation or create build.local.bat.
    exit /b 1
)

if not defined ANDROID_SDK_ROOT if defined ANDROID_HOME set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
if not defined ANDROID_SDK_ROOT if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_SDK_ROOT (
    echo ERROR: Set ANDROID_SDK_ROOT to an Android SDK installation.
    exit /b 1
)

set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin;%PATH%"

call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed.
    echo Make sure Android SDK and JDK are installed in the paths above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo APK built successfully:
for %%F in (app\build\outputs\apk\debug\*.apk) do (
    echo %%~fF
)

pause
