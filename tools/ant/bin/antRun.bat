@echo off

REM   Copyright (c) 2001-2002 The Apache Software Foundation.  All rights
REM   reserved.

if "%OS%"=="Windows_NT" @setlocal

if ""%1""=="""" goto runCommand

rem Change drive and directory to %1
if "%OS%"=="Windows_NT" cd /d ""%1""
if not "%OS%"=="Windows_NT" cd ""%1""
shift

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of agruments (up to the command line limit, anyway).
set ANT_RUN_CMD=%1
if ""%1""=="""" goto runCommand
shift
:loop
if ""%1""=="""" goto runCommand
set ANT_RUN_CMD=%ANT_RUN_CMD% %1
shift
goto loop

:runCommand
rem echo %ANT_RUN_CMD%
%ANT_RUN_CMD%

if "%OS%"=="Windows_NT" @endlocal

