#!/bin/bash

OUTPUT_PATH="$1"

npm install
npx ng build

mkdir -p "$OUTPUT_PATH"
cp dist/angular/browser/main.js "$OUTPUT_PATH/main.js"
cp dist/angular/browser/polyfills.js "$OUTPUT_PATH/polyfills.js"
cp dist/angular/3rdpartylicenses.txt "$OUTPUT_PATH/3rdpartylicenses.txt"
