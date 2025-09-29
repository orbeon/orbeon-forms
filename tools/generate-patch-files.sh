#!/bin/bash

if [ $# -ne 2 ]; then
    echo "Usage: $0 <original_directory> <patched_directory>"
    exit 1
fi

ORIGINAL_DIR="$1"
PATCHED_DIR="$2"

if [ ! -d "$ORIGINAL_DIR" ]; then
    echo "Error: Original directory '$ORIGINAL_DIR' does not exist"
    exit 1
fi

if [ ! -d "$PATCHED_DIR" ]; then
    echo "Error: Patched directory '$PATCHED_DIR' does not exist"
    exit 1
fi

for file in "$ORIGINAL_DIR"/*; do
    [ -f "$file" ] || continue

    filename=$(basename "$file")

    patched_file="$PATCHED_DIR/$filename"
    if [ -f "$patched_file" ]; then
        diff -u "$file" "$patched_file" > "${filename}.patch"

        if [ ! -s "${filename}.patch" ]; then
            rm "${filename}.patch"
        else
            echo "Generated ${filename}.patch"
        fi
    fi
done

echo "Done!"