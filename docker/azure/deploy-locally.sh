#!/bin/bash

source variables.sh

# Login if needed
source login.sh

# Generate self-signed certificate
source generate-self-signed-certificate.sh

# Create Entra ID configuration (test users, groups, permissions, etc.)
source create-entra-id-configuration.sh

docker rm -f orbeon-forms-azure-local

# Run Orbeon Forms locally, using the Azure Entra ID configuration for users and roles
docker run \
  --name orbeon-forms-azure-local \
  -p 8080:8080 \
  -p 8443:8443 \
  -v ~/.orbeon/license.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/license.xml \
  -v ./form-builder-permissions.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/form-builder-permissions.xml \
  -v ./properties-local.postgresql.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/properties-local.xml \
  -v ./jboss-web.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/jboss-web.xml \
  -v ./oidc.json:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/oidc.json \
  -v ./web.xml:/opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/web.xml \
  -v ./application.keystore:/opt/jboss/wildfly/standalone/configuration/application.keystore \
  -v ./standalone.postgresql.local.xml:/docker-entrypoint-wildfly.d/standalone.xml \
  "orbeon/orbeon-forms:$ORBEON_FORMS_DOCKER_TAG"
