@echo off
setlocal

@rem There is some funkiness wrt set command in a for statement in an if 
@rem block when setlocal is true.  Consequently set set bld_root up here
@rem rather than in an if block.
set this_fqn=%~f0
for %%i in ( %this_fqn% ) do set bld_root=%%~dp%i

if defined JAVA_HOME (
  if not defined DEBUG_ENABLED set DEBUG_ENABLED=false
  if not defined WEBLOGIC_HOME set WEBLOGIC_HOME=C:\bea\weblogic700\server

  set default_target=orbeon-dist-packages
  set target=%1
  if not defined target (
    set target=!default_target!
    echo Defaulting to target !default_target!
    echo .
  )

  @rem set CLASSPATH=%bld_root%lib\junit.jar
  set PATH=%JAVA_HOME%\bin;%PATH%
  set ANT_HOME=%bld_root%tools\ant
  echo "!ANT_HOME!\bin\ant.bat" "-Djava.home=!JAVA_HOME!" "-Dant.home=!ANT_HOME!" "-Ddebug.enabled=!DEBUG_ENABLED!" "-Dweblogic.home=!WEBLOGIC_HOME!" !target! %2 %3 %4 %5 %6 %7 %8 %9
  "!ANT_HOME!\bin\ant.bat" "-Djava.home=!JAVA_HOME!" "-Dant.home=!ANT_HOME!" "-Ddebug.enabled=!DEBUG_ENABLED!" "-Dweblogic.home=!WEBLOGIC_HOME!" !target! %2 %3 %4 %5 %6 %7 %8 %9
  
) else (
  echo env var JAVA_HOME must be set to the location of a jdk.
  echo The jdk version must be 1.4 or higher.
)

