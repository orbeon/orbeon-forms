#!/bin/bash

ENTRA_ID_DOMAIN=$(az rest --method get --url 'https://graph.microsoft.com/v1.0/domains' --query 'value[0].id' -o tsv 2>&1)
echo "Entra ID domain: $ENTRA_ID_DOMAIN"

user_id_from_display_name() {
  local display_name="$1"

  az ad user list --filter "displayName eq '$display_name'" --query "[0].id" -o tsv
}

user_upn_from_email() {
  local email="$1"

  # Build principal name from email and domain (external users)
  local upn="${email//[@+]/_}#EXT#@${ENTRA_ID_DOMAIN}"

  echo "$upn"
}

user_exists() {
  local upn="$1"

  az ad user show --id "$upn" &>/dev/null

  return $?
}

create_user() {
  local email="$1"
  local password="$2"
  local display_name="$3"

  local upn
  upn=$(user_upn_from_email "$email")

  if user_exists "$upn"; then
    echo "User $display_name already exists"
    return 0
  fi

  echo "Creating user $upn"

  if az ad user create \
    --display-name "$display_name" \
    --user-principal-name "$upn" \
    --password "$password"; then

    local object_id
    object_id=$(az ad user show --id "$upn" --query id -o tsv)

    echo "Setting email to $email"
    az rest --method patch \
      --url "https://graph.microsoft.com/v1.0/users/$object_id" \
      --body "{\"mail\":\"$email\"}"
  fi
}

# Create a couple of test users
create_user "$ENTRA_ID_TEST_USER_EMAIL1" "$ENTRA_ID_TEST_USER_PASSWORD1" "$ENTRA_ID_TEST_USER_DISPLAY_NAME1"
create_user "$ENTRA_ID_TEST_USER_EMAIL2" "$ENTRA_ID_TEST_USER_PASSWORD2" "$ENTRA_ID_TEST_USER_DISPLAY_NAME2"

group_exists() {
  local display_name="$1"
  az ad group show --group "$display_name" > /dev/null 2>&1
}

create_group() {
	local display_name="$1"

	if group_exists "$display_name"; then
	  echo "Group $display_name already exists"
	  return 0
  fi

	az ad group create --display-name "$display_name" --mail-nickname "$display_name"
}

create_group "$ENTRA_ID_USER_GROUP"
create_group "$ENTRA_ID_ADMIN_GROUP"

user_in_group() {
  local user_display_name="$1"
  local group_display_name="$2"

  local user_id
  user_id=$(user_id_from_display_name "$user_display_name")

  local result
  result=$(az ad group member check --group "$group_display_name" --member-id "$user_id" --query value -o tsv)

  [ "$result" = "true" ]
}

add_user_to_group() {
	local user_display_name="$1"
	local group_display_name="$2"

  local user_id
	user_id=$(user_id_from_display_name "$user_display_name")

	if user_in_group "$user_display_name" "$group_display_name"; then
    echo "User $user_display_name is already in the group $group_display_name"
    return 0
	fi

  az ad group member add --group "$group_display_name" --member-id "$user_id"
}

# Assign groups to users
add_user_to_group "$ENTRA_ID_TEST_USER_DISPLAY_NAME1" "$ENTRA_ID_USER_GROUP"
add_user_to_group "$ENTRA_ID_TEST_USER_DISPLAY_NAME2" "$ENTRA_ID_USER_GROUP"
add_user_to_group "$ENTRA_ID_TEST_USER_DISPLAY_NAME2" "$ENTRA_ID_ADMIN_GROUP"

if az ad app list \
    --filter "displayName eq '$ENTRA_ID_APP_NAME'" \
    --query "[].displayName" \
    -o tsv | grep -q "^$ENTRA_ID_APP_NAME$"; then
	echo "App $ENTRA_ID_APP_NAME already exists"
else
	# Create application
	az ad app create \
    --display-name "$ENTRA_ID_APP_NAME" \
    --web-redirect-uris "$LOCAL_APP_URL/*" \
    --sign-in-audience "AzureADMyOrg"
fi

ENTRA_ID_APP_ID=$(az ad app list --query "[?displayName=='$ENTRA_ID_APP_NAME'].appId" -o tsv)

# Add identifier URI
az ad app update --id "$ENTRA_ID_APP_ID" --identifier-uris "api://$ENTRA_ID_APP_ID"

ENTRA_ID_APP_OBJECT_ID=$(az ad app list --query "[?displayName=='$ENTRA_ID_APP_NAME'].id" -o tsv)

if [[ -n $(az ad app show \
    --id "$ENTRA_ID_APP_ID" \
    --query "api.oauth2PermissionScopes[?value=='$ENTRA_ID_SCOPE_VALUE']" \
    -o tsv) ]]; then
  echo "Scope $ENTRA_ID_SCOPE_VALUE already exists"
else
	# Add scope (az ad app update doesn't seem to work)
	az rest --method PATCH \
    --uri "https://graph.microsoft.com/v1.0/applications/$ENTRA_ID_APP_OBJECT_ID" \
    --headers "Content-Type=application/json" \
    --body "$(jq -n \
      --arg scope_id "$(uuidgen)" \
      --arg scope_value "$ENTRA_ID_SCOPE_VALUE" \
      '{
        api: {
          oauth2PermissionScopes: [{
            adminConsentDescription: "Allow the application to access groups on behalf of the signed-in user.",
            adminConsentDisplayName: "Access groups",
            id: $scope_id,
            isEnabled: true,
            type: "User",
            userConsentDescription: "Allow the application to access groups on your behalf.",
            userConsentDisplayName: "Access groups",
            value: $scope_value
          }]
        }
      }')"
fi

# Retrieve the scope ID given its name/value
ENTRA_ID_SCOPE_ID=$(az ad app show --id "$ENTRA_ID_APP_ID" \
                    --query "api.oauth2PermissionScopes[?value=='$ENTRA_ID_SCOPE_VALUE'].id" \
                    -o tsv)

# Pre-authorize the application (this is needed to include the groups in the correct OIDC token)
az ad app show --id "$ENTRA_ID_APP_ID" | \
  jq --arg app_id "$ENTRA_ID_APP_ID" \
     --arg scope_id "$ENTRA_ID_SCOPE_ID" \
     '.api.preAuthorizedApplications = [{
       "appId": $app_id,
       "delegatedPermissionIds": [$scope_id]
     }]' | \
  az rest --method PATCH --uri "https://graph.microsoft.com/v1.0/applications/$ENTRA_ID_APP_OBJECT_ID" --body @-

# Add client secret (beware: this will update any existing secret with the same name)
ENTRA_ID_CREDENTIAL_SECRET=$(az ad app credential reset \
                             --id "$ENTRA_ID_APP_ID" \
                             --display-name "Orbeon Forms Credential" \
                             --years 2 | jq -r '.password')

# Only security group membership claims will be included as group IDs
az ad app show --id "$ENTRA_ID_APP_ID" | \
  jq '.groupMembershipClaims = "SecurityGroup"' | \
  az rest --method PATCH --uri "https://graph.microsoft.com/v1.0/applications/$ENTRA_ID_APP_OBJECT_ID" --body @-

# Add optional OIDC claims (groups included as roles, email)
az ad app show --id "$ENTRA_ID_APP_ID" | \
  jq '.optionalClaims = {
    "accessToken": [{
      "additionalProperties": ["emit_as_roles"],
      "essential": false,
      "name": "groups",
      "source": null
    }],
    "idToken": [{
      "additionalProperties": ["emit_as_roles"],
      "essential": false,
      "name": "groups",
      "source": null
    },
    {
      "additionalProperties": [],
      "essential": false,
      "name": "email",
      "source": null
    }]
  }' | \
  az rest --method PATCH --uri "https://graph.microsoft.com/v1.0/applications/$ENTRA_ID_APP_OBJECT_ID" --body @-

API_MICROSOFT_GRAPH="00000003-0000-0000-c000-000000000000"
API_PERMISSION_OPENID="37f7f235-527c-4136-accd-4a02d197296e"
API_PERMISSION_EMAIL="64a6cdd6-aab1-4aaf-94b8-3cc8405e90d0"

check_and_add_permission() {
  local app_id=$1
  local api_id=$2
  local permission_id=$3
  local permission_type=$4

  # Check if permission exists (note: [] in JMESPath flattens the arrays)
  existing_permission=$(az ad app permission list \
                        --id "$app_id" \
                        --query "[?resourceAppId=='$api_id'].resourceAccess | [] | [?id=='$permission_id' && type=='$permission_type'].id" \
                        --output tsv)

  if [[ -z "$existing_permission" ]]; then
    az ad app permission add \
      --id "$app_id" \
      --api "$api_id" \
      --api-permissions "$permission_id=$permission_type"
  else
    echo "Permission $permission_id of type $permission_type already exists"
  fi
}

# Microsoft Graph permissions
check_and_add_permission "$ENTRA_ID_APP_ID" "$API_MICROSOFT_GRAPH" "$API_PERMISSION_OPENID" "Scope"
check_and_add_permission "$ENTRA_ID_APP_ID" "$API_MICROSOFT_GRAPH" "$API_PERMISSION_EMAIL" "Scope"

# App's own permissions
check_and_add_permission "$ENTRA_ID_APP_ID" "$ENTRA_ID_APP_ID" "$ENTRA_ID_SCOPE_ID" "Scope"

# Grant admin consent (for permissions above)
az ad app permission admin-consent --id "$ENTRA_ID_APP_ID"

# Get the tenant ID
ENTRA_ID_TENANT_ID=$(az account show --query tenantId -o tsv)

# Build the OIDC provider URL
ENTRA_ID_PROVIDER_URL="https://login.microsoftonline.com/$ENTRA_ID_TENANT_ID/v2.0"

# Build the API scope URL
ENTRA_ID_API_SCOPE_URL="api://$ENTRA_ID_APP_ID/$ENTRA_ID_SCOPE_VALUE"

# Generate the OIDC configuration file
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

ENTRA_ID_USER_GROUP_ID=$(az ad group show --group "$ENTRA_ID_USER_GROUP" --query id -o tsv)
ENTRA_ID_ADMIN_GROUP_ID=$(az ad group show --group "$ENTRA_ID_ADMIN_GROUP" --query id -o tsv)

# Generate the Form Builder permissions file
cat << EOF > form-builder-permissions.xml
<roles>
  <role name="$ENTRA_ID_ADMIN_GROUP_ID" app="*" form="*"/>
</roles>
EOF

# Generate the web.xml configuration file
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

# Print all IDs/URLs
echo "User group ID: $ENTRA_ID_USER_GROUP_ID"
echo "Admin group ID: $ENTRA_ID_ADMIN_GROUP_ID"
echo "App ID: $ENTRA_ID_APP_ID"
echo "App object ID: $ENTRA_ID_APP_OBJECT_ID"
echo "Scope ID: $ENTRA_ID_SCOPE_ID"
echo "Tenant ID: $ENTRA_ID_TENANT_ID"
echo "Provider URL: $ENTRA_ID_PROVIDER_URL"
echo "API scope URL: $ENTRA_ID_API_SCOPE_URL"

# TODO: disable MFA (can't seem to be able to do it via the CLI => Entra ID > Properties > Security defaults > disable, all from the UI)
