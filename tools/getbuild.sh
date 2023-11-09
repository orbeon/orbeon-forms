#!/bin/zsh

if [[ $# -ne 1 ]] || [[ ! $1 =~ '^[0-9]{4}$' ]]; then
  # If the provided parameter isn't four digits
  echo "Usage: $0 <four_digit_parameter>"
  exit 1
fi

version=$1
s3_path="s3://orbeon-builds/orbeon/orbeon-forms-pe/$version/$version.1/build/distrib/"
zip_file=$(aws s3 ls $s3_path | awk '/\.zip$/ && !/-src\.zip$/ {print $4}')
echo "Found ${zip_file} to download."

if [[ -z "$zip_file" ]]; then
  echo "No .zip file found to download."
  exit 1
fi

aws s3 cp "${s3_path}${zip_file}" .
echo "Downloaded ${zip_file} to the current directory."
