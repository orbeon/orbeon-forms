#!/bin/bash

set -e

WILDFLY_HOME=/opt/jboss/wildfly
DEPLOYMENT_DIR=$WILDFLY_HOME/standalone/deployments

if [ "$EMBEDDING_WAR" = "1" ] || [ "${EMBEDDING_WAR,,}" = "true" ]; then
    if [ -e /docker-entrypoint-wildfly.d/orbeon-embedding.war ]; then
        mv /docker-entrypoint-wildfly.d/orbeon-embedding.war $DEPLOYMENT_DIR/
        touch $DEPLOYMENT_DIR/orbeon-embedding.war.dodeploy
    fi
fi

# Copy any custom standalone.xml file to WildFly configuration directory
if [ -f /docker-entrypoint-wildfly.d/standalone.xml ]; then
    echo "Found custom standalone.xml, copying to WildFly configuration directory..."
    cp /docker-entrypoint-wildfly.d/standalone.xml /opt/jboss/wildfly/standalone/configuration/

    DOC_URL='https://doc.orbeon.com/installation/docker#wildfly-image'

    # WildFly 40 and newer can't start with the configuration files of older versions
    WILDFLY_MAJOR_VERSION="${WILDFLY_VERSION%%.*}"
    if [ "${WILDFLY_MAJOR_VERSION:-0}" -ge 40 ] 2>/dev/null \
       && grep -q -e 'org.wildfly.extension.security.manager' -e 'urn:jboss:domain:security-manager' -e '<policy name="jacc">' /docker-entrypoint-wildfly.d/standalone.xml; then
        echo "WARNING: the custom standalone.xml was created for an older version of WildFly, and WildFly $WILDFLY_VERSION is unlikely to start with it."
        echo "WARNING: remove the security manager extension, the security manager subsystem, and the \"jacc\" policy from the file,"
        echo "WARNING: or use the image tag with the \"-wildfly39\" suffix. See $DOC_URL"
    fi

    # Data sources refer to JDBC drivers by their deployment name
    for driver in $(sed -n 's|.*<driver>\(.*\.jar\)</driver>.*|\1|p' /docker-entrypoint-wildfly.d/standalone.xml | sort -u); do
        if [ ! -e "$DEPLOYMENT_DIR/$driver" ]; then
            echo "WARNING: the custom standalone.xml refers to the JDBC driver \"$driver\", which is not deployed."
            echo "WARNING: deployed JDBC drivers: $(cd $DEPLOYMENT_DIR && ls *.jar 2>/dev/null | tr '\n' ' '). See $DOC_URL"
        fi
    done
fi

# Execute the command passed to docker run (or the default CMD)
exec "$@"
