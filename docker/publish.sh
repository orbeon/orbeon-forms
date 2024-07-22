#!/bin/bash

publish=true

VERSION=${1:-'2023.1.3-pe'}
TAG=${2:-'tag-release-2023.1.3-pe-pseudo'}
FILE=${3:-'orbeon-2023.1.3.202406131619-PE.zip'}
SQL_FILE=${4:-'2023.1/postgresql-2023_1.sql'}
DEMO_FORMS_LICENSE_FILE=${5:-"$HOME/.orbeon/license.xml"}
DEMO_FORMS_POSTGRES_NETWORK=${6:-'demo_forms_network'}
DEMO_FORMS_ORBEON_FORMS_PORT=${7:-'18080'}

echo "Version: $VERSION"
echo "Tag: $TAG"
echo "File: $FILE"
echo "SQL file: $SQL_FILE"
echo "Demo forms license file: $DEMO_FORMS_LICENSE_FILE"
echo "Demo forms PostgreSQL network: $DEMO_FORMS_POSTGRES_NETWORK"
echo "Demo forms Orbeon Forms port: $DEMO_FORMS_ORBEON_FORMS_PORT"

# SQL script import order is defined by lexicographical order (hence 01-, 02-, etc.)
SQL_SCHEMA_FILE='01-orbeon-postgresql-schema.sql'
SQL_DATA_FILE='02-orbeon-postgresql-data.sql'

# Image names
POSTGRES_IMAGE="orbeon/postgres:$VERSION"
ORBEON_FORMS_BASE_IMAGE="orbeon/orbeon-forms-base:$VERSION"
ORBEON_FORMS_IMAGE="orbeon/orbeon-forms:$VERSION"

# Image/container names for demo forms import/export
DEMO_FORMS_POSTGRES_IMAGE="orbeon/postgres-demo-forms:$VERSION"
DEMO_FORMS_POSTGRES_CONTAINER='orbeon-postgres-demo-forms'
DEMO_FORMS_ORBEON_FORMS_CONTAINER='orbeon-orbeon-forms-demo-forms'

main() {
  trap cleanup EXIT

  # Copy the SQL file in the current directory, as COPY in Dockerfile doesn't support accessing files outside the build
  # context (i.e. the ".." below is problematic)
  cp "../form-runner/jvm/src/main/resources/apps/fr/persistence/relational/ddl/$SQL_FILE" "$SQL_SCHEMA_FILE"

  if $publish; then
    docker login -u orbeon
  fi

  # Build Orbeon Forms image (base image)
  docker build -f Dockerfile.orbeon_forms --build-arg tag="$TAG" --build-arg file="$FILE" -t "$ORBEON_FORMS_BASE_IMAGE" .

  # Build Orbeon Forms image (including PostgreSQL JDBC driver)
  docker build -f Dockerfile.orbeon_forms.postgres --build-arg base_image="$ORBEON_FORMS_BASE_IMAGE" -t "$ORBEON_FORMS_IMAGE" .

  # Do not import demo forms into PostgreSQL, just use SQLite
  ## Import demo forms into PostgreSQL database and export them to SQL script
  #create_demo_forms_sql_script

  # Build PostgreSQL image with demo forms data (as local SQL file)
  docker build -f Dockerfile.postgres -t "$POSTGRES_IMAGE" .

  if $publish; then
    docker push "$ORBEON_FORMS_IMAGE"
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

  images=("$POSTGRES_IMAGE" "$ORBEON_FORMS_BASE_IMAGE" "$ORBEON_FORMS_IMAGE" "$DEMO_FORMS_POSTGRES_IMAGE" )
  for image in "${images[@]}"; do
    if [ "$(docker images -q "$image")" ]; then
      docker rmi "$image"
    fi
  done
}

main
