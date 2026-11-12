@echo off
rem jdbx wrapper for Windows: finds a Java 25+ and the jdbx jar, then runs the jar.
rem Arguments, stdin and exit code are those of the jar.
rem
rem Jar lookup:  %JDBX_JAR%, <script dir>\jdbx.jar, <script dir>\..\lib\jdbx.jar,
rem              <repo root>\target\jdbx-*.jar
rem Java lookup: %JDBX_JAVA%, %JAVA_HOME%\bin\java.exe, java.exe on PATH (must be 25 or newer)
rem JVM options: %JDBX_OPTS% (placed before -jar)
rem Exit codes of the wrapper itself: 127 no suitable Java, 1 jar not found.

setlocal EnableExtensions DisableDelayedExpansion
set "REQUIRED_JAVA=25"
set "SCRIPT_DIR=%~dp0"

set "JAVA="
if defined JDBX_JAVA (
    set "CANDIDATE=%JDBX_JAVA%"
    call :java_ok
    if not errorlevel 1 set "JAVA=%JDBX_JAVA%"
    goto :java_done
)
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "CANDIDATE=%JAVA_HOME%\bin\java.exe"
        call :java_ok
        if not errorlevel 1 set "JAVA=%JAVA_HOME%\bin\java.exe"
        goto :java_done
    )
)
for /f "delims=" %%p in ('where java.exe 2^>nul') do (
    if not defined JAVA (
        set "CANDIDATE=%%p"
        call :java_ok
        if not errorlevel 1 set "JAVA=%%p"
    )
)
:java_done
if not defined JAVA (
    >&2 echo jdbx: no suitable Java found. jdbx needs Java %REQUIRED_JAVA% or newer. Set JDBX_JAVA to a java executable, or JAVA_HOME to a JDK %REQUIRED_JAVA%+ directory, or put java %REQUIRED_JAVA%+ on PATH.
    exit /b 127
)

set "JAR="
if defined JDBX_JAR (
    set "JAR=%JDBX_JAR%"
    goto :jar_done
)
if exist "%SCRIPT_DIR%jdbx.jar" (
    set "JAR=%SCRIPT_DIR%jdbx.jar"
    goto :jar_done
)
if exist "%SCRIPT_DIR%..\lib\jdbx.jar" (
    set "JAR=%SCRIPT_DIR%..\lib\jdbx.jar"
    goto :jar_done
)
for %%f in ("%SCRIPT_DIR%..\target\jdbx-*.jar") do (
    if not defined JAR (
        echo %%~nxf| findstr /i /e /c:"-sources.jar" /c:"-javadoc.jar" >nul
        if errorlevel 1 set "JAR=%%~f"
    )
)
:jar_done
if not defined JAR (
    >&2 echo jdbx: jar not found. Build it with 'mvnw -DskipTests package' or set JDBX_JAR.
    exit /b 1
)
if not exist "%JAR%" (
    >&2 echo jdbx: jar not found: %JAR%. Build it with 'mvnw -DskipTests package' or set JDBX_JAR.
    exit /b 1
)

"%JAVA%" %JDBX_OPTS% -jar "%JAR%" %*
exit /b %ERRORLEVEL%

rem Checks that %CANDIDATE% is a java.exe of version REQUIRED_JAVA or newer. Sets errorlevel 0 or 1.
rem The directory of the candidate goes first on PATH, so that no quoted command is needed inside for /f.
:java_ok
setlocal
for %%i in ("%CANDIDATE%") do set "CANDIDATE_DIR=%%~dpi"
if not exist "%CANDIDATE%" endlocal & exit /b 1
set "PATH=%CANDIDATE_DIR%;%PATH%"
set "JAVA_VERSION="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /c:" version "') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~v"
if not defined JAVA_VERSION endlocal & exit /b 1
set "JAVA_MAJOR="
for /f "delims=.-+_ " %%m in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%m"
if not defined JAVA_MAJOR endlocal & exit /b 1
echo %JAVA_MAJOR%| findstr /r "^[0-9][0-9]*$" >nul
if errorlevel 1 endlocal & exit /b 1
if %JAVA_MAJOR% LSS %REQUIRED_JAVA% endlocal & exit /b 1
endlocal & exit /b 0
