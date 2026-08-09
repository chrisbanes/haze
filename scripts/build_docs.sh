#!/bin/sh

./gradlew \
    --no-scan \
    :internal:dokka:dokkaGenerate \
    :sample:web:wasmJsBrowserDistribution \
    :sample:web:jsBrowserDistribution

rm -rf docs/api/
cp -r internal/dokka/build/dokka/html/ docs/api/

rm -rf docs/sample/
mkdir -p docs/sample
cp -r sample/web/build/dist/wasmJs/productionExecutable/ docs/sample/wasm/
cp -r sample/web/build/dist/js/productionExecutable/ docs/sample/js/

cp CHANGELOG.md docs/changelog.md

snapshot_version="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)"
case "$snapshot_version" in
  ''|*[!0-9A-Za-z._+-]*)
    echo "Invalid VERSION_NAME: $snapshot_version"
    exit 1
    ;;
esac

mkdir -p docs/javascripts
printf '%s\n' \
  '<?xml version="1.0" encoding="UTF-8"?>' \
  '<metadata>' \
  '  <versioning>' \
  "    <latest>$snapshot_version</latest>" \
  '  </versioning>' \
  '</metadata>' \
  > docs/javascripts/snapshot-metadata.xml

mkdocs $@
