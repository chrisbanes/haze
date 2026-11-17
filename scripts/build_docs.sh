#!/bin/sh

set -eu

./gradlew \
    --no-scan \
    :internal:dokka:dokkaGenerate \
    :sample:web:wasmJsBrowserDistribution

rm -rf docs/api/
cp -r internal/dokka/build/dokka/html/ docs/api/

rm -rf docs/sample/
mkdir -p docs/sample
cp -r sample/web/build/dist/wasmJs/productionExecutable/ docs/sample/wasm/

# Older changelog entries use HTML details blocks, where Markdown links are left as text.
sed 's|\[\([^]]*\)\](https://chrisbanes\.github\.io/haze/migrating-2\.0/)|<a href="../migrating-2.0/">\1</a>|g' \
    CHANGELOG.md > docs/changelog.md

mkdir -p docs/javascripts
curl --fail --location --silent --show-error \
  --output docs/javascripts/snapshot-metadata.xml \
  https://central.sonatype.com/repository/maven-snapshots/dev/chrisbanes/haze/haze/maven-metadata.xml

properdocs "$@"
