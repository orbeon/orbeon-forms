#! /bin/sh

. ./env.sh

export ANT_HOME=${BUILD_ROOT}/tools/ant
export CLASSPATH=${BUILD_ROOT}/build/tools/ant/tasks
export DEBUG_ENABLED=false

${ANT_HOME}/bin/ant -Djava.home=${JAVA_HOME} -Dant.home=${ANT_HOME} -Dbuild.root=${BUILD_ROOT} -Ddebug.enabled=${DEBUG_ENABLED} -Dweblogic.home=${WEBLOGIC_HOME} $*
