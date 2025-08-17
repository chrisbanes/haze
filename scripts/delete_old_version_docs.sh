#!/bin/zsh

versions=($(mike list | tr ' ' '\n'))

declare -A major_latest

# Find latest per X.Y
for v in "${versions[@]}"; do
  major="${v%.*}"
  if [[ -z "${major_latest[$major]}" ]]; then
    major_latest[$major]="$v"
  else
    latest="${major_latest[$major]}"
    # Use version sort (-V) to compare
    greater=$(printf "%s\n%s\n" "$latest" "$v" | sort -V | tail -n1)
    major_latest[$major]="$greater"
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
  mike delete "$v" --push
done
