@echo off

REM  Copyright 2001,2004 The Apache Software Foundation
REM
REM  Licensed under the Apache License, Version 2.0 (the "License");
REM  you may not use this file except in compliance with the License.
REM  You may obtain a copy of the License at
REM
REM      http://www.apache.org/licenses/LICENSE-2.0
REM
REM  Unless required by applicable law or agreed to in writing, software
REM  distributed under the License is distributed on an "AS IS" BASIS,
REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM  See the License for the specific language governing permissions and
REM  limitations under the License.

if exist "%HOME%\antrc_pre.bat" call "%HOME%\antrc_pre.bat"

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT
set DEFAULT_ANT_HOME=%~dp0..

 

if "%ANT_HOME%"=="" set ANT_HOME=%DEFAULT_ANT_HOME%
set DEFAULT_ANT_HOME=

echo %ANT_HOME%

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of arguments (up to the command line limit, anyway).
set ANT_CMD_LINE_ARGS=%1
if ""%1""=="""" goto doneStart
shift
:setupArgs
if ""%1""=="""" goto doneStart
set ANT_CMD_LINE_ARGS=%ANT_CMD_LINE_ARGS% %1
shift
goto setupArgs
rem This label provides a place for the argument list loop to break out
rem and for NT handling to skip to.

:doneStart
rem find ANT_HOME if it does not exist due to either an invalid value passed
rem by the user or the %0 problem on Windows 9x
if exist "%ANT_HOME%\lib\ant.jar" goto checkJava

rem check for ant in Program Files
if not exist "%ProgramFiles%\ant" goto checkSystemDrive
set ANT_HOME=%ProgramFiles%\ant
goto checkJava

:checkSystemDrive
rem check for ant in root directory of system drive
if not exist %SystemDrive%\ant\lib\ant.jar goto checkCDrive
set ANT_HOME=%SystemDrive%\ant
goto checkJava

:checkCDrive
rem check for ant in C:\ant for Win9X users
if not exist C:\ant\lib\ant.jar goto noAntHome
set ANT_HOME=C:\ant
goto checkJava

:noAntHome
echo ANT_HOME is set incorrectly or ant could not be located. Please set ANT_HOME.
goto end

:checkJava
set _JAVACMD=%JAVACMD%

if "%JAVA_HOME%" == "" goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=%JAVA_HOME%\bin\java.exe
goto checkJikes

:noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=java.exe

:checkJikes
if not "%JIKESPATH%"=="" goto runAntWithJikes

:runAnt
if not "%CLASSPATH%"=="" goto runAntWithClasspath
"%_JAVACMD%" %ANT_OPTS% -classpath "%ANT_HOME%\lib\ant-launcher.jar" "-Dant.home=%ANT_HOME%" org.apache.tools.ant.launch.Launcher %ANT_ARGS% %ANT_CMD_LINE_ARGS%
goto end

:runAntWithClasspath
"%_JAVACMD%" %ANT_OPTS% -classpath "%ANT_HOME%\lib\ant-launcher.jar" "-Dant.home=%ANT_HOME%" org.apache.tools.ant.launch.Launcher %ANT_ARGS% -lib "%CLASSPATH%" %ANT_CMD_LINE_ARGS%
goto end

:runAntWithJikes
if not "%CLASSPATH%"=="" goto runAntWithJikesAndClasspath
"%_JAVACMD%" %ANT_OPTS% -classpath "%ANT_HOME%\lib\ant-launcher.jar" "-Dant.home=%ANT_HOME%" "-Djikes.class.path=%JIKESPATH%" org.apache.tools.ant.launch.Launcher %ANT_ARGS% %ANT_CMD_LINE_ARGS%
goto end

:runAntWithJikesAndClasspath
"%_JAVACMD%" %ANT_OPTS% -classpath "%ANT_HOME%\lib\ant-launcher.jar" "-Dant.home=%ANT_HOME%" "-Djikes.class.path=%JIKESPATH%" org.apache.tools.ant.launch.Launcher %ANT_ARGS%  -lib "%CLASSPATH%" %ANT_CMD_LINE_ARGS%
goto end

:end
set _JAVACMD=
set ANT_CMD_LINE_ARGS=

if "%OS%"=="Windows_NT" @endlocal

:mainEnd
if exist "%HOME%\antrc_post.bat" call "%HOME%\antrc_post.bat"

