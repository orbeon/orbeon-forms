#!/bin/bash

set -e

TOMCAT_HOME=/usr/local/tomcat
DEPLOYMENT_DIR=$TOMCAT_HOME/webapps

if [ "$EMBEDDING_WAR" = "1" ] || [ "${EMBEDDING_WAR,,}" = "true" ]; then
    if [ -f /docker-entrypoint-tomcat.d/orbeon-embedding.war ]; then
        mv /docker-entrypoint-tomcat.d/orbeon-embedding.war $DEPLOYMENT_DIR/
    fi
fi

# Execute the command passed to docker run (or the default CMD)
exec "$@"
