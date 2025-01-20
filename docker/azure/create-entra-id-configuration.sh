#!/bin/bash

# This script uses: az (Azure CLI), jq (JSON manipulation), grep, and sed

# Azure account email
ACCOUNT_EMAIL="..." # TODO: add your Azure account email here

# 1st Entra ID test user
TEST_USER_EMAIL1="..." # TODO: add a test email here
TEST_USER_PASSWORD1="CHANGEME!"
TEST_USER_DISPLAY_NAME1="Test User 1"

# 2nd Entra ID test user
TEST_USER_EMAIL2="..." # TODO: add a test email here
TEST_USER_PASSWORD2="CHANGEME!"
TEST_USER_DISPLAY_NAME2="Test User 2"

# Orbeon user and admin group names
USER_GROUP="orbeon-user"
ADMIN_GROUP="orbeon-admin"

# Application name and URL
APP_NAME="Orbeon Forms"
APP_URL="https://localhost:8443/orbeon"

SCOPE_VALUE="groups.access"

check_azure_login() {
  local GRAPH_SCOPE="https://graph.microsoft.com//.default"
  local EXPECTED_USER="$1"  # Pass the expected user as first argument

  # Check if we're logged in as the correct user
  local account_output
  account_output=$(az account show --query user.name -o tsv 2>&1)
  if [[ $? -ne 0 || "$account_output" != "$EXPECTED_USER" ]]; then
    echo "Need to login as $EXPECTED_USER..."
    if !  az login --scope "$GRAPH_SCOPE"; then
      return 1
    fi
  fi

  # Verify Graph API access by making a test call
  if ! az rest --method get --url 'https://graph.microsoft.com/v1.0/domains' >/dev/null 2>&1; then
    echo "Need to refresh token with Graph API permissions..."
    if ! az login --scope "$GRAPH_SCOPE"; then
      return 1
    fi
  fi

  return 0
}

if ! check_azure_login "$ACCOUNT_EMAIL"; then
  echo "Login failed"
fi

DOMAIN=$(az rest --method get --url 'https://graph.microsoft.com/v1.0/domains' --query 'value[0].id' -o tsv 2>&1)
echo "First domain: $DOMAIN"

user_id_from_display_name() {
  local display_name="$1"

  az ad user list --filter "displayName eq '$display_name'" --query "[0].id" -o tsv
}

user_upn_from_email() {
  local email="$1"

  # Build principal name from email and domain (external users)
  local upn="${email//[@+]/_}#EXT#@${DOMAIN}"

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
    return 1
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
create_user "$TEST_USER_EMAIL1" "$TEST_USER_PASSWORD1" "$TEST_USER_DISPLAY_NAME1"
create_user "$TEST_USER_EMAIL2" "$TEST_USER_PASSWORD2" "$TEST_USER_DISPLAY_NAME2"

group_exists() {
  local display_name="$1"
  az ad group show --group "$display_name" > /dev/null 2>&1
}

create_group() {
	local display_name="$1"

	if group_exists "$display_name"; then
	  echo "Group $display_name already exists"
	  return 1
  fi

	az ad group create --display-name "$display_name" --mail-nickname "$display_name"
}

create_group "$USER_GROUP"
create_group "$ADMIN_GROUP"

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
    return 1
	fi

  az ad group member add --group "$group_display_name" --member-id "$user_id"
}

# Assign groups to users
add_user_to_group "$TEST_USER_DISPLAY_NAME1" "$USER_GROUP"
add_user_to_group "$TEST_USER_DISPLAY_NAME2" "$USER_GROUP"
add_user_to_group "$TEST_USER_DISPLAY_NAME2" "$ADMIN_GROUP"

app_exists() {
	local app_name=$1

	az ad app list --filter "displayName eq '$app_name'" --query "[].displayName" -o tsv | grep -q "^$app_name$"
}

if app_exists "$APP_NAME"; then
	echo "App $APP_NAME already exists"
else
	# Create application
	az ad app create \
    --display-name "$APP_NAME" \
    --web-redirect-uris "$APP_URL/*" \
    --sign-in-audience "AzureADMyOrg"
fi

APP_ID=$(az ad app list --query "[?displayName=='$APP_NAME'].appId" -o tsv)

# Add identifier URI
az ad app update --id "$APP_ID" --identifier-uris "api://$APP_ID"

APP_OBJECT_ID=$(az ad app list --query "[?displayName=='$APP_NAME'].id" -o tsv)

scope_exists() {
  local app_id="$1"
  local scope_value="$2"

  local scope_exists
  scope_exists=$(az ad app show --id "$app_id" --query "api.oauth2PermissionScopes[?value=='$scope_value']" -o tsv)

  [ -n "$scope_exists" ]
}

if scope_exists "$APP_ID" "$SCOPE_VALUE"; then
  echo "Scope $SCOPE_VALUE already exists"
else
	# Add scope (az ad app update doesn't seem to work)
	az rest --method PATCH \
    --uri "https://graph.microsoft.com/v1.0/applications/$APP_OBJECT_ID" \
    --headers "Content-Type=application/json" \
    --body "$(jq -n \
      --arg scope_id "$(uuidgen)" \
      --arg scope_value "$SCOPE_VALUE" \
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
SCOPE_ID=$(az ad app show --id "$APP_ID" \
           --query "api.oauth2PermissionScopes[?value=='$SCOPE_VALUE'].id" \
           -o tsv)

# Pre-authorize the application (this is needed to include the groups in the correct OIDC token)
az ad app show --id "$APP_ID" | \
  jq --arg app_id "$APP_ID" \
     --arg scope_id "$SCOPE_ID" \
     '.api.preAuthorizedApplications = [{
       "appId": $app_id,
       "delegatedPermissionIds": [$scope_id]
     }]' | \
  az rest --method PATCH --uri "https://graph.microsoft.com/v1.0/applications/$APP_OBJECT_ID" --body @-

# Add client secret (beware: this will update any existing secret with the same name)
CREDENTIAL_SECRET=$(az ad app credential reset \
                    --id "$APP_ID" \
                    --display-name "Orbeon Forms Credential" \
                    --years 2 | jq -r '.password')

# Only security group membership claims will be included as group IDs
az ad app show --id "$APP_ID" | \
  jq '.groupMembershipClaims = "SecurityGroup"' | \
  az rest --method PATCH --uri "https://graph.microsoft.com/v1.0/applications/$APP_OBJECT_ID" --body @-

# Add optional OIDC claims (groups included as roles, email)
az ad app show --id "$APP_ID" | \
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
  az rest --method PATCH --uri "https://graph.microsoft.com/v1.0/applications/$APP_OBJECT_ID" --body @-

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
check_and_add_permission "$APP_ID" "$API_MICROSOFT_GRAPH" "$API_PERMISSION_OPENID" "Scope"
check_and_add_permission "$APP_ID" "$API_MICROSOFT_GRAPH" "$API_PERMISSION_EMAIL" "Scope"

# App's own permissions
check_and_add_permission "$APP_ID" "$APP_ID" "$SCOPE_ID" "Scope"

# Grant admin consent (for permissions above)
az ad app permission admin-consent --id "$APP_ID"

# Get the tenant ID
TENANT_ID=$(az account show --query tenantId -o tsv)

# Build the OIDC provider URL
PROVIDER_URL="https://login.microsoftonline.com/$TENANT_ID/v2.0"

# Build the API scope URL
API_SCOPE_URL="api://$APP_ID/$SCOPE_VALUE"

# Generate the OIDC configuration file
cat << EOF > oidc.json
{
  "client-id": "$APP_ID",
  "provider-url": "$PROVIDER_URL",
  "credentials": {
    "secret": "$CREDENTIAL_SECRET"
  },
  "principal-attribute": "oid",
  "scope": "profile $API_SCOPE_URL"
}
EOF

USER_GROUP_ID=$(az ad group show --group "$USER_GROUP" --query id -o tsv)
ADMIN_GROUP_ID=$(az ad group show --group "$ADMIN_GROUP" --query id -o tsv)

# Generate the Form Builder permissions file
cat << EOF > form-builder-permissions.xml
<roles>
  <role name="$ADMIN_GROUP_ID" app="*" form="*"/>
</roles>
EOF

# Generate the web.xml configuration file
cp web.template.xml web.xml
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' \
        -e "s/USER_GROUP_ID/${USER_GROUP_ID}/g" \
        -e "s/ADMIN_GROUP_ID/${ADMIN_GROUP_ID}/g" \
        web.xml
else
    # Linux and other Unix-like systems
    sed -i \
        -e "s/USER_GROUP_ID/${USER_GROUP_ID}/g" \
        -e "s/ADMIN_GROUP_ID/${ADMIN_GROUP_ID}/g" \
        web.xml
fi

# Print all IDs/URLs
echo "User group ID: $USER_GROUP_ID"
echo "Admin group ID: $ADMIN_GROUP_ID"
echo "App ID: $APP_ID"
echo "App object ID: $APP_OBJECT_ID"
echo "Scope ID: $SCOPE_ID"
echo "Tenant ID: $TENANT_ID"
echo "Provider URL: $PROVIDER_URL"
echo "API scope URL: $API_SCOPE_URL"

# TODO: disable MFA (can't seem to be able to do it via the CLI => Entra ID > Properties > Security defaults > disable, all from the UI)
