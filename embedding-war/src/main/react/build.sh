#!/bin/bash
npm install
npm run build
mkdir -p ../webapp/assets/react
cp dist/main.js ../webapp/assets/react/main.js
