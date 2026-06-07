#!/bin/zsh

# Parse mike list output. Format: "version [alias1, alias2]  title"
# We only care about version names (first column) and aliases.
# Aliases like 'latest' and 'dev' must never be deleted.

latest_alias_version=""
dev_alias_version=""
bare_versions=()

while IFS= read -r line; do
  version=$(echo "$line" | awk '{print $1}')
  version_aliases=$(echo "$line" | grep -o '\[.*\]' | tr -d '[]' | tr ',' '\n' | xargs)

  bare_versions+=("$version")

  if echo "$version_aliases" | grep -qw "latest"; then
    latest_alias_version="$version"
  fi
  if echo "$version_aliases" | grep -qw "dev"; then
    dev_alias_version="$version"
  fi
done < <(mike list)

declare -A major_latest

for v in "${bare_versions[@]}"; do
  major="${v%.*}"
  if [[ -z "${major_latest[$major]}" ]]; then
    major_latest[$major]="$v"
  else
    prev="${major_latest[$major]}"
    greater=$(printf "%s\n%s\n" "$prev" "$v" | sort -V | tail -n1)
    major_latest[$major]="$greater"
  fi
done

to_delete=()
for v in "${bare_versions[@]}"; do
  # Never delete the version holding the 'latest' or 'dev' alias
  if [[ "$v" == "$latest_alias_version" ]]; then
    echo "Keeping $v (holds 'latest' alias)"
    continue
  fi
  if [[ "$v" == "$dev_alias_version" ]]; then
    echo "Keeping $v (holds 'dev' alias)"
    continue
  fi

  major="${v%.*}"
  if [[ "$v" != "${major_latest[$major]}" ]]; then
    to_delete+=("$v")
  fi
done

for v in "${to_delete[@]}"; do
  echo "Deleting $v"
  mike delete "$v" --push
done
