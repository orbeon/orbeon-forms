#!/bin/bash

# Generate OIDC configuration file (app ID, provider URL, credentials, API scope)
cat << EOF > oidc.json
{
  "client-id": "$ENTRA_ID_APP_ID",
  "provider-url": "$ENTRA_ID_PROVIDER_URL",
  "credentials": {
    "secret": "$ENTRA_ID_CREDENTIAL_SECRET"
  },
  "principal-attribute": "oid",
  "scope": "profile $ENTRA_ID_API_SCOPE_URL"
}
EOF

# Generate Form Builder permissions file (admin group ID)
cat << EOF > form-builder-permissions.xml
<roles>
  <role name="$ENTRA_ID_ADMIN_GROUP_ID" app="*" form="*"/>
</roles>
EOF

# Generate web.xml configuration file (user/admin group IDs)
cp web.template.xml web.xml
if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS
  sed -i '' \
      -e "s/ENTRA_ID_USER_GROUP_ID/${ENTRA_ID_USER_GROUP_ID}/g" \
      -e "s/ENTRA_ID_ADMIN_GROUP_ID/${ENTRA_ID_ADMIN_GROUP_ID}/g" \
      web.xml
else
  # Linux and other Unix-like systems
  sed -i \
      -e "s/ENTRA_ID_USER_GROUP_ID/${ENTRA_ID_USER_GROUP_ID}/g" \
      -e "s/ENTRA_ID_ADMIN_GROUP_ID/${ENTRA_ID_ADMIN_GROUP_ID}/g" \
      web.xml
fi

# Generate WildFly's standalone.xml configuration file (PostgreSQL URL)
cp standalone.postgresql.azure.xml standalone.xml
if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS
  sed -i '' \
      -e "s/DATABASE_SERVER/${DATABASE_SERVER}/g" \
      standalone.xml
else
  # Linux and other Unix-like systems
  sed -i \
      -e "s/DATABASE_SERVER/${DATABASE_SERVER}/g" \
      standalone.xml
fi
