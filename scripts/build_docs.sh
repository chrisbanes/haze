#!/bin/sh

./gradlew \
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

mkdocs $@
