#!/bin/bash

publish=true
delete_local_images_after_publish=true

VERSION=${1:-'2023.1.3-pe'}
TAG=${2:-'tag-release-2023.1.3-pe-pseudo'}
FILE=${3:-'orbeon-2023.1.3.202406131619-PE.zip'}
SQL_FILE=${4:-'2023.1/postgresql-2023_1.sql'}

echo "Version: $VERSION"
echo "Tag: $TAG"
echo "File: $FILE"
echo "SQL file: $SQL_FILE"

if $publish; then
  docker login -u orbeon
fi

docker build -f Dockerfile_orbeon_forms --build-arg tag="$TAG" --build-arg file="$FILE" -t orbeon/orbeon-forms:"$VERSION" .
docker build -f Dockerfile_postgres --build-arg sql_file="$SQL_FILE" -t orbeon/postgres:"$VERSION" .

if $publish; then
  docker push orbeon/orbeon-forms:"$VERSION"
  docker push orbeon/postgres:"$VERSION"
fi

if $delete_local_images_after_publish; then
  docker rmi orbeon/orbeon-forms:"$VERSION"
  docker rmi orbeon/postgres:"$VERSION"
fi
