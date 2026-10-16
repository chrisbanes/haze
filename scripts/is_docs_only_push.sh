#!/bin/sh

set -eu

before=$1
after=$2

if [ "$before" = "0000000000000000000000000000000000000000" ]; then
  before=$(git mktree </dev/null)
else
  git fetch --no-tags --depth=1 origin "$before"
fi

# Inspect the complete push; GitHub's workflow path filters are limited to 300 files.
if git diff --quiet --no-renames "$before" "$after" -- . \
    ':(exclude,glob)**/*.md' ':(exclude,glob)docs/**'; then
  echo true
else
  status=$?
  if [ "$status" -ne 1 ]; then
    exit "$status"
  fi
  echo false
fi
