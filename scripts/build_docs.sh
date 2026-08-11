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

cp CHANGELOG.md docs/changelog.md

mkdir -p docs/javascripts
curl --fail --location --silent --show-error \
  --output docs/javascripts/snapshot-metadata.xml \
  https://central.sonatype.com/repository/maven-snapshots/dev/chrisbanes/haze/haze/maven-metadata.xml

mkdocs "$@"
