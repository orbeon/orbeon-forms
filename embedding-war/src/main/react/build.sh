#!/bin/bash

OUTPUT_PATH="$1"

npm install --ignore-scripts
npm run build

mkdir -p "$OUTPUT_PATH"
cp dist/main.js "$OUTPUT_PATH/main.js"
