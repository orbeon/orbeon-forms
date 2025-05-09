#!/bin/bash

set -e

WILDFLY_HOME=/opt/jboss/wildfly
DEPLOYMENT_DIR=$WILDFLY_HOME/standalone/deployments

if [ "$EMBEDDING_WAR" = "1" ] || [ "${EMBEDDING_WAR,,}" = "true" ]; then
    if [ -e /docker-entrypoint-wildfly.d/orbeon-embedding.war ]; then
        mv /docker-entrypoint-wildfly.d/orbeon-embedding.war $DEPLOYMENT_DIR/
    fi
fi

# Copy any custom standalone.xml file to WildFly configuration directory
if [ -f /docker-entrypoint-wildfly.d/standalone.xml ]; then
    echo "Found custom standalone.xml, copying to WildFly configuration directory..."
    cp /docker-entrypoint-wildfly.d/standalone.xml /opt/jboss/wildfly/standalone/configuration/
fi

# Execute the command passed to docker run (or the default CMD)
exec "$@"
