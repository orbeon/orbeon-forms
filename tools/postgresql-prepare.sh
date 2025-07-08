#!/bin/bash

# Turn on command echoing
set -x

# Clean existing installations and configurations
sudo apt-get -y purge \
  postgresql-9.4 \
  postgresql-9.5 \
  postgresql-9.6 \
  postgresql-10 \
  postgresql-client-9.4 \
  postgresql-client-9.5 \
  postgresql-client-9.6 \
  postgresql-client-10
sudo apt-get -y autoremove

# Install and start PostgreSQL 12
sudo apt-get -y install postgresql-12 postgresql-client-12 postgresql-contrib-12
sudo -i -u postgres sed -i '1i host all orbeon 127.0.0.1/32 trust' /etc/postgresql/12/main/pg_hba.conf
sudo systemctl start postgresql@12-main

# Create the orbeon user and database
sudo -i -u postgres createuser --no-password --superuser orbeon
sudo -i -u postgres createdb --owner=orbeon orbeon
