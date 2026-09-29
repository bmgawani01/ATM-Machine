#!/bin/sh
# Builds the app as ONE self-contained native application:
#   ./package-app.sh            -> dist-app/ATM/ATM  (needs jpackage + a JDK)
#   ./package-app.sh -Installer -> a .dmg for macOS
set -e
cd "$(dirname "$0")"

TYPE="app-image"
if [ "$1" = "-Installer" ]; then
  TYPE="pkg"
fi

echo "==> 1/2 building target/atm-system.jar"
mvn -B -q package

echo "==> 2/2 running jpackage"
rm -rf dist-app
jpackage --type "$TYPE" \
         --name ATM \
         --input target \
         --main-jar atm-system.jar \
         --main-class atm.system.AtmSystem \
         --app-version 1.0.0 \
         --vendor "ATM System" \
         --dest dist-app

echo ""
echo "Done. Run it from dist-app/ATM/"
