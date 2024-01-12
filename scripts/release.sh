#!/usr/bin/env bash

set -exo pipefail

# Gets a property out of a .properties file
# usage: getProperty $key $filename
function getProperty() {
    grep "${1}" "$2" | cut -d'=' -f2
}

NEW_VERSION=$1
NEW_SNAPSHOT_VERSION=$2
CUR_SNAPSHOT_VERSION=$(getProperty 'VERSION_NAME' gradle.properties)

if [ -z "$NEW_SNAPSHOT_VERSION" ]; then
  # If no snapshot version was provided, use the current value
  NEW_SNAPSHOT_VERSION=$CUR_SNAPSHOT_VERSION
fi

echo "Publishing $NEW_VERSION"

# Prepare release
sed -i.bak "s/${CUR_SNAPSHOT_VERSION}/${NEW_VERSION}/g" gradle.properties
git add gradle.properties
git commit -m "Prepare for release $NEW_VERSION"

# Build
./gradlew build

# Add git tag
git tag "v$NEW_VERSION"
# Prepare next snapshot
echo "Setting next snapshot version $NEW_SNAPSHOT_VERSION"
sed -i.bak "s/${NEW_VERSION}/${NEW_SNAPSHOT_VERSION}/g" gradle.properties
git add gradle.properties
git commit -m "Prepare next development version"

# Remove the backup file from sed edits
rm gradle.properties.bak

# Push it all up
git push && git push --tags
