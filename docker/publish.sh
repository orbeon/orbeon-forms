#!/bin/bash

# In case of device space issues, make the "Disk usage limit" higher under "Resource Allocation" (Docker Desktop)

REMOTE_PUBLISH=false

VERSION=${1:-'2025.1-pe'}
RELEASE_TAG=${2:-'tag-release-2025.1-ce'}
FILE=${3:-'orbeon-2025.1.202512302330-PE.zip'}
SHA256_CHECKSUM=${4:-'db0d1828c959a25d347db37bbab9f564f0daa63d6d84e38d1a998551734563ad'}
SQL_FILE=${5:-'2026.1/postgresql-2026_1.sql'}
PLATFORMS=${6:-'linux/amd64,linux/arm64'}
DEMO_FORMS_LICENSE_FILE=${7:-"$HOME/.orbeon/license.xml"}
DEMO_FORMS_POSTGRES_NETWORK=${8:-'demo_forms_network'}
DEMO_FORMS_ORBEON_FORMS_PORT=${9:-'18080'}

echo "Version: $VERSION"
echo "Release tag: $RELEASE_TAG"
echo "File: $FILE"
echo "SHA-256 checksum: $SHA256_CHECKSUM"
echo "SQL file: $SQL_FILE"
echo "Platforms: $PLATFORMS"
echo "Demo forms license file: $DEMO_FORMS_LICENSE_FILE"
echo "Demo forms PostgreSQL network: $DEMO_FORMS_POSTGRES_NETWORK"
echo "Demo forms Orbeon Forms port: $DEMO_FORMS_ORBEON_FORMS_PORT"
echo

# SQL script import order is defined by lexicographical order (hence 01-, 02-, etc.)
SQL_SCHEMA_FILE='01-orbeon-postgresql-schema.sql'
SQL_DATA_FILE='02-orbeon-postgresql-data.sql'

# Docker registry tags
DOCKER_TAG_BASE="$VERSION"
DOCKER_TAG_TOMCAT="$DOCKER_TAG_BASE"
DOCKER_TAG_WILDFLY="$DOCKER_TAG_BASE-wildfly"

# Image names
POSTGRES_IMAGE="orbeon/postgres:$DOCKER_TAG_BASE"
ORBEON_FORMS_TOMCAT_BASE_IMAGE="orbeon/orbeon-forms-base:$DOCKER_TAG_TOMCAT"
ORBEON_FORMS_TOMCAT_IMAGE="orbeon/orbeon-forms:$DOCKER_TAG_TOMCAT"
ORBEON_FORMS_WILDFLY_BASE_IMAGE="orbeon/orbeon-forms-base:$DOCKER_TAG_WILDFLY"
ORBEON_FORMS_WILDFLY_IMAGE="orbeon/orbeon-forms:$DOCKER_TAG_WILDFLY"

# Image/container names for demo forms import/export
DEMO_FORMS_POSTGRES_IMAGE="orbeon/postgres-demo-forms:$DOCKER_TAG_BASE"
DEMO_FORMS_POSTGRES_CONTAINER='orbeon-postgres-demo-forms'
DEMO_FORMS_ORBEON_FORMS_CONTAINER='orbeon-orbeon-forms-demo-forms'

main() {
  trap cleanup EXIT

  # This script needs the 'Use containerd for pulling and storing images' option enabled in Docker Desktop
  if ! docker info 2>/dev/null | grep -q "driver-type: io.containerd"; then
      echo "This script needs the 'Use containerd for pulling and storing images' option enabled in Docker Desktop"
      exit 1
  fi

  # Copy the SQL file in the current directory, as COPY in Dockerfile doesn't support accessing files outside the build
  # context (i.e. the ".." below is problematic)
  cp "../form-runner/jvm/src/main/resources/apps/fr/persistence/relational/ddl/$SQL_FILE" "$SQL_SCHEMA_FILE"

  if $REMOTE_PUBLISH; then
    docker login -u orbeon
  fi

  # Build Orbeon Forms image (Tomcat, base image)
  docker build --platform="$PLATFORMS" -f Dockerfile.orbeon_forms.tomcat --build-arg tag="$RELEASE_TAG" --build-arg file="$FILE" --build-arg sha256_checksum="$SHA256_CHECKSUM" -t "$ORBEON_FORMS_TOMCAT_BASE_IMAGE" .

  # Build Orbeon Forms image (Tomcat, including PostgreSQL JDBC driver)
  docker build --platform="$PLATFORMS" -f Dockerfile.orbeon_forms.tomcat.postgres_driver --build-arg base_image="$ORBEON_FORMS_TOMCAT_BASE_IMAGE" -t "$ORBEON_FORMS_TOMCAT_IMAGE" .

  # Build Orbeon Forms image (WildFly, base image)
  docker build --platform="$PLATFORMS" -f Dockerfile.orbeon_forms.wildfly --build-arg tag="$RELEASE_TAG" --build-arg file="$FILE" --build-arg sha256_checksum="$SHA256_CHECKSUM" -t "$ORBEON_FORMS_WILDFLY_BASE_IMAGE" .

  # Build Orbeon Forms image (WildFly, including PostgreSQL JDBC driver)
  docker build --platform="$PLATFORMS" -f Dockerfile.orbeon_forms.wildfly.postgres_driver --build-arg base_image="$ORBEON_FORMS_WILDFLY_BASE_IMAGE" -t "$ORBEON_FORMS_WILDFLY_IMAGE" .

  # Do not import demo forms into PostgreSQL, just use SQLite
  ## Import demo forms into PostgreSQL database and export them to SQL script
  #create_demo_forms_sql_script

  # Build PostgreSQL image with demo forms data (as local SQL file)
  docker build --platform="$PLATFORMS" -f Dockerfile.postgres -t "$POSTGRES_IMAGE" .

  if $REMOTE_PUBLISH; then
    docker push "$ORBEON_FORMS_TOMCAT_IMAGE"
    docker push "$ORBEON_FORMS_WILDFLY_IMAGE"
    docker push "$POSTGRES_IMAGE"
  fi
}

is_postgres_ready() {
  docker exec "$DEMO_FORMS_POSTGRES_CONTAINER" pg_isready -d orbeon -U orbeon > /dev/null 2>&1
}

is_orbeon_forms_ready() {
  curl -s -o /dev/null "http://localhost:$DEMO_FORMS_ORBEON_FORMS_PORT/orbeon"
}

create_demo_forms_sql_script() {
  # Build and run PostgreSQL container and wait until it's ready
  docker build -f Dockerfile.postgres -t "$DEMO_FORMS_POSTGRES_IMAGE" .
  docker network create "$DEMO_FORMS_POSTGRES_NETWORK"
  docker run --name "$DEMO_FORMS_POSTGRES_CONTAINER" \
             --network="$DEMO_FORMS_POSTGRES_NETWORK" \
             -e POSTGRES_DB=orbeon \
             -e POSTGRES_USER=orbeon \
             -e POSTGRES_PASSWORD=orbeon \
             -d "$DEMO_FORMS_POSTGRES_IMAGE"

  until is_postgres_ready; do
      echo "Waiting for PostgreSQL to be ready..."
      sleep 1
  done

  # Run Orbeon Forms container and wait until it's ready
  docker run --name "$DEMO_FORMS_ORBEON_FORMS_CONTAINER" \
             --network="$DEMO_FORMS_POSTGRES_NETWORK" \
             -p $DEMO_FORMS_ORBEON_FORMS_PORT:8080 \
             -v "$DEMO_FORMS_LICENSE_FILE:/usr/local/tomcat/webapps/orbeon/WEB-INF/resources/config/license.xml" \
             -v "./log4j2-demo-forms.xml:/usr/local/tomcat/webapps/orbeon/WEB-INF/resources/config/log4j2.xml" \
             -v "./orbeon-demo-forms.xml:/usr/local/tomcat/conf/Catalina/localhost/orbeon.xml" \
             -v "./properties-local-demo-forms.xml:/usr/local/tomcat/webapps/orbeon/WEB-INF/resources/config/properties-local.xml" \
             -d "$ORBEON_FORMS_IMAGE"

  until is_orbeon_forms_ready; do
    echo "Waiting for Orbeon Forms to be ready..."
    sleep 1
  done

  # Import demo forms (from data folder)
  pushd ..
  amm ammonite/import-sample-data-into-demo.sc "http://localhost:$DEMO_FORMS_ORBEON_FORMS_PORT/orbeon"
  popd

  # Export demo forms (to SQL script)
  docker exec -i "$DEMO_FORMS_POSTGRES_CONTAINER" \
         /bin/bash -c "PGPASSWORD=orbeon pg_dump --column-inserts --data-only --username orbeon orbeon" > "$SQL_DATA_FILE"
}

cleanup() {
  files=("$SQL_SCHEMA_FILE" "$SQL_DATA_FILE")
  for file in "${files[@]}"; do
    if [ -f "$file" ]; then
      rm "$file"
    fi
  done

  containers=("$DEMO_FORMS_ORBEON_FORMS_CONTAINER" "$DEMO_FORMS_POSTGRES_CONTAINER")
  for container in "${containers[@]}"; do
    if [ "$(docker ps -a -q -f name=^${container}$)" ]; then
      docker rm -f "$container"
    fi
  done

  networks=("$DEMO_FORMS_POSTGRES_NETWORK")
  for network in "${networks[@]}"; do
    if [ "$(docker network ls -q -f name=^${network}$)" ]; then
      docker network rm "$network"
    fi
  done

  images=("$POSTGRES_IMAGE" "$ORBEON_FORMS_TOMCAT_BASE_IMAGE" "$ORBEON_FORMS_TOMCAT_IMAGE" "$ORBEON_FORMS_WILDFLY_BASE_IMAGE" "$ORBEON_FORMS_WILDFLY_IMAGE" "$DEMO_FORMS_POSTGRES_IMAGE" )
  for image in "${images[@]}"; do
    if [ "$(docker images -q "$image")" ]; then
      docker rmi "$image"
    fi
  done
}

main
