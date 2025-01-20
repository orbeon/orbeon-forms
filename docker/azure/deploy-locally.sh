#!/bin/bash

# Create Entra ID configuration (test users, groups, permissions, etc.)
source create-entra-id-configuration.sh

if [ ! -f "application.keystore" ]; then
  # Generate self-signed certificate (default passwords in WildFly's configuration are both "password")
  keytool -genkey \
    -alias server \
    -keyalg RSA \
    -validity 3650 \
    -keysize 2048 \
    -keystore application.keystore \
    -storepass password \
    -keypass password \
    -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown"
fi

docker rm -f orbeon-forms-azure-local

# Run Orbeon Forms locally, using the Azure Entra ID configuration for users and roles
docker run \
  --name orbeon-forms-azure-local \
  -p 8080:8080 \
  -p 8443:8443 \
  -v ~/.orbeon/license.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/license.xml \
  -v ./form-builder-permissions.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/form-builder-permissions.xml \
  -v ./oidc.json:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/oidc.json \
  -v ./web.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/web.xml \
  -v ./application.keystore:/opt/jboss/wildfly/standalone/configuration/application.keystore \
  orbeon/orbeon-forms:2024.1-pe-wildfly
