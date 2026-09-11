#!/usr/bin/env python3

import sys
import re
import shutil
import subprocess
from pathlib import Path
from datetime import date


def run(cmd: list[str], check: bool = True) -> None:
    print(f"  Running: {' '.join(cmd)}")
    subprocess.run(cmd, check=check)


def get_property(key: str, file: Path) -> str:
    for line in file.read_text().splitlines():
        if line.startswith(f"{key}="):
            return line.split("=", 1)[1].strip()
    raise ValueError(f"Property {key} not found in {file}")


def set_property(key: str, value: str, file: Path) -> None:
    content = file.read_text()
    content = re.sub(rf"^{re.escape(key)}=.*$", f"{key}={value}", content, flags=re.MULTILINE)
    file.write_text(content)


def find_published_api_files() -> list[Path]:
    api_files = []
    for api_txt in Path(".").rglob("api/api.txt"):
        module_dir = api_txt.parent.parent
        props_file = module_dir / "gradle.properties"
        if props_file.exists():
            content = props_file.read_text()
            if re.search(r"^POM_ARTIFACT_ID=", content, re.MULTILINE):
                api_files.append(api_txt)
                print(f"  Found published module: {module_dir}")
            else:
                print(f"  Skipping unpublished module: {module_dir}")
    return sorted(api_files)


def update_changelog(version: str, changelog: Path) -> None:
    content = changelog.read_text()
    today = date.today().strftime("%Y-%m-%d")
    
    if "## Unreleased" not in content:
        raise ValueError("CHANGELOG.md missing '## Unreleased' section")
    
    new_header = f"## {version} <small>{today}</small> {{ id=\"{version}\" }}"
    
    parts = content.split("## Unreleased", 1)
    if len(parts) != 2:
        raise ValueError("CHANGELOG.md malformed")
    
    before, after = parts
    new_content = f"{before}## Unreleased\n\n## {version} <small>{today}</small> {{ id=\"{version}\" }}{after}"
    changelog.write_text(new_content)


def main():
    if len(sys.argv) < 2:
        print("Usage: release.py <release-version> [next-snapshot-version]")
        print("  e.g. python3 release.py 2.0.0")
        print("  e.g. python3 release.py 2.0.0 2.1.0-SNAPSHOT")
        sys.exit(1)
    
    new_version = sys.argv[1]
    gradle_props = Path("gradle.properties")
    changelog = Path("CHANGELOG.md")
    
    cur_snapshot = get_property("VERSION_NAME", gradle_props)
    
    if not cur_snapshot.endswith("-SNAPSHOT"):
        print(f"Error: Current VERSION_NAME ({cur_snapshot}) is not a -SNAPSHOT version")
        sys.exit(1)
    
    if new_version.endswith("-SNAPSHOT"):
        print(f"Error: Release version ({new_version}) must not be a -SNAPSHOT version")
        sys.exit(1)
    
    if len(sys.argv) >= 3:
        next_snapshot = sys.argv[2]
    else:
        base = new_version.split("-")[0]
        major, minor, patch = base.split(".")
        next_snapshot = f"{major}.{minor}.{int(patch) + 1}-SNAPSHOT"
    
    print("=" * 50)
    print(f"  Releasing:     {new_version}")
    print(f"  From:          {cur_snapshot}")
    print(f"  Next snapshot: {next_snapshot}")
    print("=" * 50)
    
    print("\nStep 1: Bumping version in gradle.properties...")
    set_property("VERSION_NAME", new_version, gradle_props)
    
    print("\nStep 2: Running ./gradlew publish...")
    run(["./gradlew", "publish", "--no-configuration-cache"])
    
    print("\nStep 3: Copying api.txt snapshots for published modules...")
    api_files = find_published_api_files()
    
    if not api_files:
        print("  Warning: No published modules with api.txt found")
    
    for api_txt in api_files:
        version_txt = api_txt.parent / f"{new_version}.txt"
        shutil.copy2(api_txt, version_txt)
        print(f"  Copied {api_txt} -> {version_txt}")
    
    print("\nStep 4: Updating CHANGELOG.md...")
    update_changelog(new_version, changelog)
    
    print("\nStep 5: Committing and tagging...")
    run(["git", "add", str(gradle_props), str(changelog)])
    for api_txt in api_files:
        version_txt = api_txt.parent / f"{new_version}.txt"
        run(["git", "add", str(version_txt)])
    
    run(["git", "commit", "-m", f"Prepare for release {new_version}"])
    run(["git", "tag", new_version])
    
    print("\nStep 6: Pushing tag...")
    run(["git", "push", "origin", new_version])
    
    print(f"\nStep 7: Setting next snapshot version ({next_snapshot})...")
    set_property("VERSION_NAME", next_snapshot, gradle_props)
    run(["git", "add", str(gradle_props)])
    run(["git", "commit", "-m", "Prepare next development version"])
    run(["git", "push"])
    
    print("\n" + "=" * 50)
    print(f"  Release {new_version} complete!")
    print(f"  Next snapshot: {next_snapshot}")
    print("=" * 50)


if __name__ == "__main__":
    main()
