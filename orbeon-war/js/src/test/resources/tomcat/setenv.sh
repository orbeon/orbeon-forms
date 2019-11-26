ORBEON_MEMORY_OPTS="-Xms300m -Xmx2g -verbosegc -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -XX:+PrintGCDetails"
ORBEON_DEBUG_OPTS=""

JAVA_OPTS="-ea $ORBEON_MEMORY_OPTS $ORBEON_DEBUG_OPTS -Djava.net.preferIPv4Stack=true"

export JAVA_OPTS
