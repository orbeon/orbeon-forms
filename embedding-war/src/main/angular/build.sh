#!/bin/bash
npm install
ng build
mkdir -p ../webapp/assets/angular
cp dist/angular/browser/main.js ../webapp/assets/angular/main.js
cp dist/angular/browser/polyfills.js ../webapp/assets/angular/polyfills.js
cp dist/angular/3rdpartylicenses.txt ../webapp/assets/angular/3rdpartylicenses.txt
