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

# Sortable key: core version, then 0+prerelease or 1 for stable, so a stable release outranks
# its own prereleases; sort -V compares numeric suffixes numerically (alpha2 < alpha10).
release_key() {
  local core="${1%%-*}" pre=""
  [[ "$1" == *-* ]] && pre="${1#*-}"
  if [[ -z "$pre" ]]; then
    printf "%s\t1\n" "$core"
  else
    printf "%s\t0%s\n" "$core" "$pre"
  fi
}

# Minor line, e.g. 2.1.0-alpha01 -> 2.1. Names without a major.minor prefix are their own line.
minor_of() {
  if [[ "$1" =~ ^([0-9]+\.[0-9]+) ]]; then echo "${match[1]}"; else echo "$1"; fi
}

declare -A major_latest

for v in "${bare_versions[@]}"; do
  major=$(minor_of "$v")
  if [[ -z "${major_latest[$major]}" ]]; then
    major_latest[$major]="$v"
  else
    prev="${major_latest[$major]}"
    greater=$(
      { printf "%s\t%s\n" "$(release_key "$prev")" "$prev"; printf "%s\t%s\n" "$(release_key "$v")" "$v"; } |
        sort -t$'\t' -k1,1V -k2,2V | tail -n1 | cut -f3
    )
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

  major=$(minor_of "$v")
  if [[ "$v" != "${major_latest[$major]}" ]]; then
    to_delete+=("$v")
  fi
done

for v in "${to_delete[@]}"; do
  echo "Deleting $v"
  mike delete "$v"
done
