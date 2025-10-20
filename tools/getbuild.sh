#!/bin/zsh

# Parse optional flags
extract=false
cleanup=false
while getopts "ec" opt; do
  case $opt in
    e) extract=true ;;
    c) cleanup=true ;;
    \?) echo "Invalid option: -$OPTARG" >&2; exit 1 ;;
  esac
done

# Shift past the optional arguments
shift $((OPTIND-1))

# Check remaining required arguments
if [[ $# -ne 2 ]] || [[ ! $1 =~ ^(ce|pe)$ ]] || [[ ! $2 =~ '^[0-9]{4,5}$' ]]; then
  echo "Usage: $0 [-e] [-c] ce|pe <four_digit_parameter>"
  echo "Options:"
  echo "  -e    Extract the zip file after download"
  echo "  -c    Clean up (delete) the zip file after processing"
  exit 1
fi

# Download the zip file
edition=$([[ $1 == "pe" ]] && echo "-pe")
version=$2
s3_path="s3://orbeon-builds/orbeon/orbeon-forms$edition/$version/$version.1/build/distrib/"
zip_file=$(aws s3 ls $s3_path | awk '/\.zip$/ && !/-src\.zip$/ {print $4}')
echo "Found ${zip_file} to download."
if [[ -z "$zip_file" ]]; then
  echo "No .zip file found to download."
  exit 1
fi
aws s3 cp "${s3_path}${zip_file}" .
echo "Downloaded ${zip_file} to the current directory."

# Extract `orbeon.war` if -e flag was provided
if $extract; then
  base_name=${zip_file%.zip}
  echo "Extracting ${base_name}/orbeon.war to current directory..."
  unzip -j -q "${zip_file}" "${base_name}/orbeon.war"
fi

# Clean up if -c flag was provided
if $cleanup; then
  echo "Cleaning up ${zip_file}..."
  rm "${zip_file}"
fi
