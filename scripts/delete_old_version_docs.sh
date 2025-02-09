#!/bin/zsh

versions=($(mike list | tr ' ' '\n'))

declare -A major_latest

for v in "${versions[@]}"; do
  major="${v%.*}"
  if [[ -z "${major_latest[$major]}" || "$v" > "${major_latest[$major]}" ]]; then
    major_latest[$major]="$v"
  fi
done

to_delete=()
for v in "${versions[@]}"; do
  major="${v%.*}"
  if [[ "$v" != "${major_latest[$major]}" ]]; then
    to_delete+=("$v")
  fi
done

for v in "${to_delete[@]}"; do
  echo "Deleting $v"
  mike delete $v --push
done
