@echo off
@setlocal

rem ------------------------------------------------------
rem Check environment variables

if not "%JAVA_HOME%" == "" goto java_home_ok
echo Environment variable JAVA_HOME must be set.
set ERROR=true
:java_home_ok

if not "%DEBUG_ENABLED%" == "" goto debug_enabled_ok
set DEBUG_ENABLED=false
:debug_enabled_ok

if not "%WEBLOGIC_HOME%" == "" goto weblogic_home_ok
set WEBLOGIC_HOME=C:\bea\weblogic700\server
:weblogic_home_ok

if "%ERROR%" == "true" goto end

rem ------------------------------------------------------
rem Run Ant

set target=%1
set default_target=orbeon-dist-packages
if not defined target (
    set target=%default_target%
    echo Defaulting to target %default_target%
)

for %%I in (.) do set BUILD_ROOT=%%~fI
set CLASSPATH=.\lib\junit.jar
set PATH=%JAVA_HOME%\bin;%PATH%
set ANT_HOME=tools\ant
"%ANT_HOME%\bin\ant.bat" "-Djava.home=%JAVA_HOME%" "-Dant.home=%ANT_HOME%" "-Dbuild.root=%BUILD_ROOT%" "-Ddebug.enabled=%DEBUG_ENABLED%" "-Dweblogic.home=%WEBLOGIC_HOME%" %target% %2 %3 %4 %5 %6 %7 %8 %9

:end
