#!/bin/bash

TODAY=`date +"%Y%m%d"`
WWW_DIR="/var/www/geogit"

cd $WORKSPACE/src/parent
mvn clean install -DskipTests

cd $WORKSPACE/src/cli-app
mvn assembly:assembly -DskipTests
cp $WORKSPACE/src/cli-app/target/geogit-cli-app-*.zip $WWW_DIR/geogit-cli-app-0.1-$TODAY.zip
ln -sf $WWW_DIR/geogit-cli-app-0.1-$TODAY.zip $WWW_DIR/geogit-cli-app-0.1-latest.zip
