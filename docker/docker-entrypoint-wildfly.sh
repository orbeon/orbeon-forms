#!/bin/bash

set -e

# Copy any custom standalone.xml file to WildFly configuration directory
if [ -f /docker-entrypoint-wildfly.d/standalone.xml ]; then
    echo "Found custom standalone.xml, copying to WildFly configuration directory..."
    cp /docker-entrypoint-wildfly.d/standalone.xml /opt/jboss/wildfly/standalone/configuration/
fi

# Execute the command passed to docker run (or the default CMD)
exec "$@"
