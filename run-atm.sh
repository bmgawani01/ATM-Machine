#!/bin/sh
# ATM System - run the whole app on Linux/macOS
# Database settings come from ATM_DB_* environment variables, or from
# atm-db.properties next to this script.
set -e
cd "$(dirname "$0")"

if [ ! -f target/atm-system.jar ]; then
  echo "Building atm-system.jar ..."
  mvn -B -q package
fi

exec java -jar target/atm-system.jar "$@"
