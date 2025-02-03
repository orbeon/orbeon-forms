#!/bin/bash

if [ ! -f "application.keystore" ]; then
  # Generate self-signed certificate (default passwords in WildFly's configuration are both "password")
  keytool -genkey \
    -alias server \
    -keyalg RSA \
    -validity 3650 \
    -keysize 2048 \
    -keystore application.keystore \
    -storepass password \
    -keypass password \
    -dname "CN=localhost, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown"
fi
