#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
capture_tool_dir="$(CDPATH= cd -- "$script_dir/.." && pwd -P)"
validator="$script_dir/capture-references.sh"
bundle_dir="$(mktemp -d)"
trap 'rm -rf "$bundle_dir"' EXIT

expect_success() {
    if ! "$@"; then
        printf 'Expected success: %s\n' "$*" >&2
        exit 1
    fi
}

expect_failure() {
    if "$@"; then
        printf 'Expected failure: %s\n' "$*" >&2
        exit 1
    fi
}

snapshot_five_file_bundle() {
    local source="$1"
    local destination="$2"
    local filename

    mkdir "$destination"
    for filename in \
        manifest.json \
        uniform-light.png \
        uniform-dark.png \
        grid-light.png \
        grid-dark.png; do
        cp "$source/$filename" "$destination/$filename"
    done
}

assert_bundle_matches_snapshot() {
    local actual="$1"
    local expected="$2"
    local context="$3"

    if ! diff -r "$expected" "$actual" >/dev/null; then
        printf '%s did not preserve exact five-file bundle\n' "$context" >&2
        exit 1
    fi
}

read_sips_property() {
    local image_path="$1"
    local property="$2"
    local output
    local line
    local key
    local value

    output="$(sips -g "$property" "$image_path")"
    while IFS= read -r line; do
        key="${line%%:*}"
        key="${key//[[:space:]]/}"
        if [[ "$key" == "$property" ]]; then
            value="${line#*:}"
            value="${value#"${value%%[![:space:]]*}"}"
            value="${value%"${value##*[![:space:]]}"}"
            printf '%s\n' "$value"
            return 0
        fi
    done <<< "$output"

    printf 'Missing sips property %s for %s\n' "$property" "$image_path" >&2
    exit 1
}

swift_helper="$bundle_dir/write-png.swift"
cat > "$swift_helper" <<'SWIFT'
import AppKit
import Foundation

_ = NSApplication.shared

let arguments = CommandLine.arguments
guard arguments.count == 4,
      let width = Int(arguments[2]),
      let height = Int(arguments[3]) else {
    fatalError("Usage: write-png.swift OUTPUT WIDTH HEIGHT")
}

let output = URL(fileURLWithPath: arguments[1])
guard let image = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: width,
    pixelsHigh: height,
    bitsPerSample: 8,
    samplesPerPixel: 3,
    hasAlpha: false,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0
) else {
    fatalError("Unable to create bitmap")
}

let canvas = NSImage(size: NSSize(width: width, height: height))
canvas.addRepresentation(image)
canvas.lockFocus()
guard let context = NSGraphicsContext.current else {
    fatalError("Unable to create graphics context")
}

context.saveGraphicsState()
NSColor(red: 0.25, green: 0.5, blue: 0.75, alpha: 1).setFill()
NSBezierPath(rect: NSRect(x: 0, y: 0, width: width, height: height)).fill()
context.restoreGraphicsState()
canvas.unlockFocus()

guard let png = image.representation(using: .png, properties: [:]) else {
    fatalError("Unable to encode PNG")
}

try png.write(to: output)
SWIFT

source_png="$bundle_dir/source.png"
swift "$swift_helper" "$source_png" 1080 2160
source_color_space="$(read_sips_property "$source_png" space)"

jpeg_helper="$bundle_dir/write-jpeg.swift"
cat > "$jpeg_helper" <<'SWIFT'
import AppKit
import Foundation

let arguments = CommandLine.arguments
guard arguments.count == 3 else {
    fatalError("Usage: write-jpeg.swift INPUT OUTPUT")
}

let input = try Data(contentsOf: URL(fileURLWithPath: arguments[1]))
guard let image = NSBitmapImageRep(data: input),
      let jpeg = image.representation(using: .jpeg, properties: [:]) else {
    fatalError("Unable to encode JPEG")
}

try jpeg.write(to: URL(fileURLWithPath: arguments[2]))
SWIFT

for scene in uniform-light.png uniform-dark.png grid-light.png grid-dark.png; do
    cp "$source_png" "$bundle_dir/$scene"
done

jq -n --arg color_space "$source_color_space" '{
    schemaVersion: 1,
    platform: "iOS 26",
    osBuild: "23A5297f",
    device: "iPhone 17",
    colorSpace: $color_space,
    scale: 3,
    material: "Regular",
    tint: "transparent",
    scenes: {
        "uniform-light.png": { background: "uniform", appearance: "light" },
        "uniform-dark.png": { background: "uniform", appearance: "dark" },
        "grid-light.png": { background: "grid", appearance: "light" },
        "grid-dark.png": { background: "grid", appearance: "dark" }
    },
    surfaces: {
        capsule: { x: 180, y: 270, width: 720, height: 192 },
        card: { x: 120, y: 672, width: 840, height: 528 },
        panel: { x: 60, y: 1380, width: 960, height: 660 }
    },
    producer: {
        xcode: "26.0",
        runtime: "iOS 26.0",
        revision: "test"
    }
}' > "$bundle_dir/manifest.json"
cp "$bundle_dir/manifest.json" "$bundle_dir/manifest.valid.json"

expect_success "$validator" --validate-only "$bundle_dir"

swift "$jpeg_helper" "$source_png" "$bundle_dir/uniform-light.png"
expect_failure "$validator" --validate-only "$bundle_dir"
cp "$source_png" "$bundle_dir/uniform-light.png"

jq '.colorSpace = "mismatched-color-space"' "$bundle_dir/manifest.valid.json" > "$bundle_dir/manifest.json"
expect_failure "$validator" --validate-only "$bundle_dir"
cp "$bundle_dir/manifest.valid.json" "$bundle_dir/manifest.json"

bundle_parent="$(dirname "$bundle_dir")"
bundle_name="$(basename "$bundle_dir")"
expected_bundle_dir="$(CDPATH= cd -- "$bundle_dir" && pwd -P)"
expected_output="Validated iOS Glass reference bundle: $expected_bundle_dir"
if ! relative_output="$(
    cd "$bundle_parent"
    CDPATH="$bundle_parent" "$validator" --validate-only "$bundle_name"
)"; then
    printf 'Expected success with CDPATH and relative bundle directory\n' >&2
    exit 1
fi
if [[ "$relative_output" != "$expected_output" ]]; then
    printf 'Expected exact output: %s\nActual output: %s\n' "$expected_output" "$relative_output" >&2
    exit 1
fi

rm "$bundle_dir/uniform-light.png"
expect_failure "$validator" --validate-only "$bundle_dir"
cp "$source_png" "$bundle_dir/uniform-light.png"

swift "$swift_helper" "$bundle_dir/uniform-light.png" 1079 2160
expect_failure "$validator" --validate-only "$bundle_dir"
cp "$source_png" "$bundle_dir/uniform-light.png"

jq '.platform = "iOS 25"' "$bundle_dir/manifest.valid.json" > "$bundle_dir/manifest.json"
expect_failure "$validator" --validate-only "$bundle_dir"
cp "$bundle_dir/manifest.valid.json" "$bundle_dir/manifest.json"

jq '.surfaces.panel.y = 1600' "$bundle_dir/manifest.valid.json" > "$bundle_dir/manifest.json"
expect_failure "$validator" --validate-only "$bundle_dir"
cp "$bundle_dir/manifest.valid.json" "$bundle_dir/manifest.json"

fake_command_dir="$bundle_dir/fake-commands"
mkdir -p "$fake_command_dir"
fake_xcrun_log="$bundle_dir/fake-xcrun.log"
: > "$fake_xcrun_log"

cat > "$fake_command_dir/xcrun" <<'FAKE_XCRUN'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$FAKE_XCRUN_LOG"
case "$*" in
    'simctl list runtimes --json')
        printf '%s\n' '{"runtimes":[{"bundlePath":"/Library/Developer/CoreSimulator/Volumes/iOS_23D127/Library/Developer/CoreSimulator/Profiles/Runtimes/iOS 26.3.simruntime","buildversion":"23D127","identifier":"com.apple.CoreSimulator.SimRuntime.iOS-26-3","isAvailable":true,"name":"iOS 26.3","platform":"iOS","version":"26.3"}]}'
        ;;
    'simctl list devices --json')
        printf '%s\n' '{"devices":{"com.apple.CoreSimulator.SimRuntime.iOS-26-3":[{"dataPath":"/tmp/fake-simulator","dataPathSize":0,"deviceTypeIdentifier":"com.apple.CoreSimulator.SimDeviceType.iPhone-17","isAvailable":true,"lastBootedAt":"2026-07-10T00:00:00Z","logPath":"/tmp/fake-simulator/logs","logPathSize":0,"name":"Haze Glass Reference","state":"Shutdown","udid":"00000000-0000-0000-0000-000000000001"}]}}'
        ;;
    *)
        printf 'Unexpected fake xcrun invocation: %s\n' "$*" >&2
        exit 1
        ;;
esac
FAKE_XCRUN

cat > "$fake_command_dir/xcodebuild" <<'FAKE_XCODEBUILD'
#!/usr/bin/env bash
if [[ "$*" == '-version' ]]; then
    printf 'Xcode 26.3\nBuild version TEST\n'
    exit 0
fi
printf 'Intentional fake xcodebuild failure\n' >&2
exit 42
FAKE_XCODEBUILD

cat > "$fake_command_dir/jq" <<'FAKE_JQ'
#!/usr/bin/env bash
exec jq "$@"
FAKE_JQ

cat > "$fake_command_dir/git" <<'FAKE_GIT'
#!/usr/bin/env bash
set -euo pipefail

case " $* " in
    *' status '*)
        exit 0
        ;;
    *)
        exec git "$@"
        ;;
esac
FAKE_GIT

cat > "$fake_command_dir/sips" <<'FAKE_SIPS'
#!/usr/bin/env bash
exec sips "$@"
FAKE_SIPS

chmod +x \
    "$fake_command_dir/xcrun" \
    "$fake_command_dir/xcodebuild" \
    "$fake_command_dir/git" \
    "$fake_command_dir/jq" \
    "$fake_command_dir/sips"

if command_output="$({
    FAKE_XCRUN_LOG="$fake_xcrun_log" \
        XCRUN="$fake_command_dir/xcrun" \
        XCODEBUILD="$fake_command_dir/xcodebuild" \
        GIT="$fake_command_dir/git" \
        JQ="$fake_command_dir/jq" \
        SIPS="$fake_command_dir/sips" \
        "$validator"
} 2>&1)"; then
    printf 'Expected fake default capture to fail after simulator discovery\n' >&2
    exit 1
fi

assert_log_line() {
    local expected="$1"
    local line

    while IFS= read -r line; do
        if [[ "$line" == "$expected" ]]; then
            return 0
        fi
    done < "$fake_xcrun_log"

    printf 'Expected fake xcrun invocation: %s\nCapture output:\n%s\n' \
        "$expected" "$command_output" >&2
    exit 1
}

assert_log_line 'simctl list runtimes --json'
assert_log_line 'simctl list devices --json'

if [[ "$command_output" == *'capture mode is not implemented'* ]]; then
    printf 'Default command still reports unimplemented capture mode\n' >&2
    exit 1
fi

full_fake_command_dir="$bundle_dir/full-fake-commands"
mkdir -p "$full_fake_command_dir"
full_fake_xcrun_log="$bundle_dir/full-fake-xcrun.log"
full_fake_xcodebuild_log="$bundle_dir/full-fake-xcodebuild.log"
full_fake_sips_log="$bundle_dir/full-fake-sips.log"
full_fake_data_container="$bundle_dir/full-fake-data-container"
full_fake_capture_root="$bundle_dir/full-fake-captures"
full_fake_lock_dir="$bundle_dir/full-fake-capture.lock"
full_fake_import_destination="$bundle_dir/import-parent/ios26"
full_fake_app_path="$bundle_dir/full-fake-derived-data/Build/Products/Release-iphonesimulator/Glass Reference Capture.app"
mkdir -p "$full_fake_data_container/Documents" "$full_fake_import_destination"
: > "$full_fake_xcrun_log"
: > "$full_fake_xcodebuild_log"
: > "$full_fake_sips_log"
printf 'old destination\n' > "$full_fake_import_destination/old-marker.txt"

cat > "$full_fake_command_dir/xcrun" <<'FULL_FAKE_XCRUN'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$FAKE_XCRUN_LOG"
case "$1 $2" in
    'simctl list')
        case "$3" in
            runtimes)
                [[ "$4" == '--json' ]]
                printf '%s\n' '{"runtimes":[{"bundlePath":"/Library/Developer/CoreSimulator/Volumes/iOS_23D127/Library/Developer/CoreSimulator/Profiles/Runtimes/iOS 26.3.simruntime","buildversion":"23D127","identifier":"com.apple.CoreSimulator.SimRuntime.iOS-26-3","isAvailable":true,"name":"iOS 26.3","platform":"iOS","version":"26.3"}]}'
                ;;
            devices)
                [[ "$4" == '--json' ]]
                printf '%s\n' '{"devices":{"com.apple.CoreSimulator.SimRuntime.iOS-26-3":[{"dataPath":"/tmp/unrelated","dataPathSize":0,"deviceTypeIdentifier":"com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro","isAvailable":true,"name":"Unrelated Simulator","state":"Booted","udid":"FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"},{"dataPath":"/tmp/fake-simulator-b","dataPathSize":0,"deviceTypeIdentifier":"com.apple.CoreSimulator.SimDeviceType.iPhone-17","isAvailable":true,"name":"Haze Glass Reference","state":"Shutdown","udid":"BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB"},{"dataPath":"/tmp/fake-simulator-a","dataPathSize":0,"deviceTypeIdentifier":"com.apple.CoreSimulator.SimDeviceType.iPhone-17","isAvailable":true,"name":"Haze Glass Reference","state":"Shutdown","udid":"AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"}]}}'
                ;;
            *)
                exit 91
                ;;
        esac
        ;;
    'simctl boot')
        [[ "$3" == 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA' ]]
        ;;
    'simctl bootstatus')
        [[ "$3" == 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA' && "$4" == '-b' ]]
        ;;
    'simctl install')
        [[ "$3" == 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA' ]]
        [[ "$4" == "$FAKE_APP_PATH" ]]
        ;;
    'simctl get_app_container')
        [[ "$3" == 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA' ]]
        [[ "$4" == 'dev.chrisbanes.haze.glassreferencecapture' ]]
        [[ "$5" == 'data' ]]
        printf '%s\n' "$FAKE_DATA_CONTAINER"
        ;;
    'simctl launch')
        [[ "$3" == '--terminate-running-process' ]]
        [[ "$4" == 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA' ]]
        [[ "$5" == 'dev.chrisbanes.haze.glassreferencecapture' ]]
        [[ "$6" == '--capture-scene' ]]
        scene="$7"
        if [[ "${FAKE_FAIL_SCENE:-}" == "$scene" ]]; then
            printf 'Intentional fake launch failure for %s\n' "$scene" >&2
            exit 92
        fi
        cat > "$FAKE_DATA_CONTAINER/Documents/capture-ready.json" <<READY
{"schemaVersion":1,"scene":"$scene","scale":3,"colorSpace":"sRGB","framebuffer":{"x":0,"y":0,"width":1206,"height":2622},"safeAreaInsets":{"top":186,"leading":0,"bottom":102,"trailing":0},"viewport":{"x":63,"y":231,"width":1080,"height":2160},"surfaces":{"capsule":{"x":180,"y":270,"width":720,"height":192},"card":{"x":120,"y":672,"width":840,"height":528},"panel":{"x":60,"y":1380,"width":960,"height":660}}}
READY
        printf 'dev.chrisbanes.haze.glassreferencecapture: 12345\n'
        ;;
    'simctl io')
        [[ "$3" == 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA' ]]
        [[ "$4" == 'screenshot' ]]
        printf 'full framebuffer for %s\n' "${FAKE_CAPTURE_TAG:-capture}" > "$5"
        ;;
    *)
        printf 'Unexpected full fake xcrun invocation: %s\n' "$*" >&2
        exit 93
        ;;
esac
FULL_FAKE_XCRUN

cat > "$full_fake_command_dir/xcodebuild" <<'FULL_FAKE_XCODEBUILD'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$*" == '-version' ]]; then
    if [[ "${FAKE_HANG_XCODE_VERSION:-0}" == '1' ]]; then
        sleep 4
    fi
    printf '%b' "${FAKE_XCODE_VERSION:-Xcode 26.3\\nBuild version 17C529\\n}"
    exit 0
fi

printf '%s\n' "$*" >> "$FAKE_XCODEBUILD_LOG"
expected="-project $FAKE_PROJECT_PATH -scheme CaptureApp -configuration Release -destination id=AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA -derivedDataPath $FAKE_DERIVED_DATA_PATH CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build"
if [[ "$*" != "$expected" ]]; then
    printf 'Unexpected fake xcodebuild invocation:\n%s\nExpected:\n%s\n' "$*" "$expected" >&2
    exit 94
fi
mkdir -p "$FAKE_APP_PATH"
if [[ -n "${FAKE_MUTATE_AFTER_BUILD_MARKER:-}" ]]; then
    : > "$FAKE_MUTATE_AFTER_BUILD_MARKER"
fi
FULL_FAKE_XCODEBUILD

cat > "$full_fake_command_dir/jq" <<'FULL_FAKE_JQ'
#!/usr/bin/env bash
exec jq "$@"
FULL_FAKE_JQ

cat > "$full_fake_command_dir/git" <<'FULL_FAKE_GIT'
#!/usr/bin/env bash
set -euo pipefail

git_arguments="$*"

emit_status_record() {
    local record="$1"

    case " $git_arguments " in
        *' -z '*)
            printf '%s\0' "$record"
            ;;
        *)
            printf '%s\n' "$record"
            ;;
    esac
}

if [[ "${1:-}" == '-C' && "${2:-}" == "${FAKE_REPO_ROOT:-}" &&
    "${3:-}" == 'rev-parse' && "${4:-}" == 'HEAD' ]]; then
    printf '%s\n' "$FAKE_GIT_REVISION"
    exit 0
fi

case " $* " in
    *' status '*)
        status_call=1
        if [[ -n "${FAKE_GIT_STATUS_COUNT_FILE:-}" ]]; then
            if [[ -f "$FAKE_GIT_STATUS_COUNT_FILE" ]]; then
                status_call=$(( $(< "$FAKE_GIT_STATUS_COUNT_FILE") + 1 ))
            fi
            printf '%s\n' "$status_call" > "$FAKE_GIT_STATUS_COUNT_FILE"
        fi
        if [[ "${FAKE_GIT_DIRTY:-0}" == '1' ]]; then
            emit_status_record '?? App/UntrackedCaptureSource.swift'
        elif [[ -n "${FAKE_MUTATE_AFTER_BUILD_MARKER:-}" &&
            -e "$FAKE_MUTATE_AFTER_BUILD_MARKER" ]]; then
            emit_status_record ' M App/ReferenceSceneView.swift'
        elif [[ -n "${FAKE_GIT_DIRTY_ON_STATUS_CALL:-}" &&
            "$status_call" -eq "$FAKE_GIT_DIRTY_ON_STATUS_CALL" ]]; then
            emit_status_record ' M App/CaptureMetadata.swift'
        fi
        if [[ "${FAKE_REPORT_ACTIVE_IMPORT_TEMP:-0}" == '1' ]]; then
            for active_import_dir in \
                "$FAKE_REPO_ROOT/$FAKE_IMPORT_PARENT_RELATIVE"/.ios26.import.*; do
                if [[ -d "$active_import_dir" ]]; then
                    for active_import_file in "$active_import_dir"/*; do
                        if [[ -f "$active_import_file" ]]; then
                            emit_status_record "?? ${active_import_file#"$FAKE_REPO_ROOT/"}"
                        fi
                    done
                fi
            done
        fi
        if [[ -n "${FAKE_EXTRA_UNTRACKED_AT_STATUS_CALL:-}" &&
            "$status_call" -eq "$FAKE_EXTRA_UNTRACKED_AT_STATUS_CALL" ]]; then
            emit_status_record "?? $FAKE_IMPORT_PARENT_RELATIVE/.ios26.import.external-note.txt"
        fi
        ;;
    *)
        exec git "$@"
        ;;
esac
FULL_FAKE_GIT

cat > "$full_fake_command_dir/sips" <<'FULL_FAKE_SIPS'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$FAKE_SIPS_LOG"
case "$1" in
    -c)
        [[ "$2" == '2160' && "$3" == '1080' ]]
        [[ "$4" == '--cropOffset' && "$5" == '231' && "$6" == '63' ]]
        [[ "$8" == '--out' ]]
        printf 'cropped %s for %s\n' "${9##*/}" "${FAKE_CAPTURE_TAG:-capture}" > "$9"
        ;;
    -g)
        image_path="$3"
        case "$2" in
            format)
                value=png
                ;;
            pixelWidth)
                case "$image_path" in
                    */framebuffers/*)
                        value="${FAKE_FRAMEBUFFER_WIDTH:-1206}"
                        ;;
                    *)
                        value=1080
                        ;;
                esac
                ;;
            pixelHeight)
                case "$image_path" in
                    */framebuffers/*)
                        value="${FAKE_FRAMEBUFFER_HEIGHT:-2622}"
                        ;;
                    *)
                        value=2160
                        ;;
                esac
                ;;
            space)
                value=sRGB
                ;;
            *)
                exit 95
                ;;
        esac
        printf '%s\n  %s: %s\n' "$3" "$2" "$value"
        ;;
    *)
        exit 96
        ;;
esac
FULL_FAKE_SIPS

chmod +x \
    "$full_fake_command_dir/xcrun" \
    "$full_fake_command_dir/xcodebuild" \
    "$full_fake_command_dir/git" \
    "$full_fake_command_dir/jq" \
    "$full_fake_command_dir/sips"

run_full_fake_capture() {
    local effective_repo_root
    local effective_import_destination

    effective_repo_root="${TEST_REPO_ROOT:-$(git -C "$capture_tool_dir" rev-parse --show-toplevel)}"
    effective_import_destination="${TEST_IMPORT_DESTINATION:-$full_fake_import_destination}"
    FAKE_XCRUN_LOG="$full_fake_xcrun_log" \
        FAKE_XCODEBUILD_LOG="$full_fake_xcodebuild_log" \
        FAKE_SIPS_LOG="$full_fake_sips_log" \
        FAKE_DATA_CONTAINER="$full_fake_data_container" \
        FAKE_APP_PATH="$full_fake_app_path" \
        FAKE_PROJECT_PATH="$capture_tool_dir/GlassReferenceCapture.xcodeproj" \
        FAKE_DERIVED_DATA_PATH="$bundle_dir/full-fake-derived-data" \
        CAPTURE_ROOT="$full_fake_capture_root" \
        CAPTURE_LOCK_DIR="$full_fake_lock_dir" \
        DERIVED_DATA_DIR="$bundle_dir/full-fake-derived-data" \
        REPO_ROOT="$effective_repo_root" \
        IMPORT_DESTINATION="$effective_import_destination" \
        XCRUN="$full_fake_command_dir/xcrun" \
        XCODEBUILD="$full_fake_command_dir/xcodebuild" \
        GIT="$full_fake_command_dir/git" \
        JQ="$full_fake_command_dir/jq" \
        SIPS="$full_fake_command_dir/sips" \
        "$validator" "$@"
}

FAKE_CAPTURE_TAG=initial expect_success run_full_fake_capture --import

for expected_file in \
    manifest.json \
    uniform-light.png \
    uniform-dark.png \
    grid-light.png \
    grid-dark.png; do
    if [[ ! -f "$full_fake_import_destination/$expected_file" ]]; then
        printf 'Imported bundle is missing %s\n' "$expected_file" >&2
        exit 1
    fi
done
if [[ -e "$full_fake_import_destination/old-marker.txt" ]]; then
    printf 'Atomic import retained a stale destination file\n' >&2
    exit 1
fi

imported_file_count=0
for imported_path in "$full_fake_import_destination"/*; do
    if [[ -e "$imported_path" ]]; then
        imported_file_count=$((imported_file_count + 1))
    fi
done
if [[ "$imported_file_count" -ne 5 ]]; then
    printf 'Expected exactly five imported files, found %s\n' "$imported_file_count" >&2
    exit 1
fi

expected_revision="$(git -C "$script_dir/../../.." rev-parse HEAD)"
if ! jq -e --arg revision "$expected_revision" '
    .schemaVersion == 1 and
    .platform == "iOS 26" and
    .osBuild == "23D127" and
    .device == "iPhone 17" and
    .scale == 3 and
    .colorSpace == "sRGB" and
    .material == "Regular" and
    .tint == "transparent" and
    .producer.xcode == "Xcode 26.3\nBuild version 17C529" and
    .producer.runtime == "com.apple.CoreSimulator.SimRuntime.iOS-26-3 / iOS 26.3 / 26.3" and
    .producer.revision == $revision and
    .scenes == {
        "uniform-light.png": { appearance: "light", background: "uniform" },
        "uniform-dark.png": { appearance: "dark", background: "uniform" },
        "grid-light.png": { appearance: "light", background: "grid" },
        "grid-dark.png": { appearance: "dark", background: "grid" }
    } and
    .surfaces == {
        capsule: { x: 180, y: 270, width: 720, height: 192 },
        card: { x: 120, y: 672, width: 840, height: 528 },
        panel: { x: 60, y: 1380, width: 960, height: 660 }
    }
' "$full_fake_import_destination/manifest.json" >/dev/null; then
    printf 'Imported manifest does not contain measured producer metadata\n' >&2
    exit 1
fi

expected_launches="simctl launch --terminate-running-process AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA dev.chrisbanes.haze.glassreferencecapture --capture-scene uniform-light
simctl launch --terminate-running-process AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA dev.chrisbanes.haze.glassreferencecapture --capture-scene uniform-dark
simctl launch --terminate-running-process AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA dev.chrisbanes.haze.glassreferencecapture --capture-scene grid-light
simctl launch --terminate-running-process AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA dev.chrisbanes.haze.glassreferencecapture --capture-scene grid-dark"
actual_launches=""
while IFS= read -r invocation; do
    case "$invocation" in
        'simctl launch '*)
            if [[ -n "$actual_launches" ]]; then
                actual_launches="$actual_launches
$invocation"
            else
                actual_launches="$invocation"
            fi
            ;;
    esac
done < "$full_fake_xcrun_log"
if [[ "$actual_launches" != "$expected_launches" ]]; then
    printf 'Unexpected scene capture order:\n%s\n' "$actual_launches" >&2
    exit 1
fi

current_uniform_before_failure="$(< "$full_fake_capture_root/current/uniform-light.png")"
import_uniform_before_failure="$(< "$full_fake_import_destination/uniform-light.png")"
FAKE_FAIL_SCENE=grid-light expect_failure run_full_fake_capture --import
if [[ "$(< "$full_fake_capture_root/current/uniform-light.png")" != "$current_uniform_before_failure" ]]; then
    printf 'Failed capture replaced the prior validated current staging bundle\n' >&2
    exit 1
fi
if [[ "$(< "$full_fake_import_destination/uniform-light.png")" != "$import_uniform_before_failure" ]]; then
    printf 'Failed fresh capture modified the import destination\n' >&2
    exit 1
fi

fake_mv_dir="$bundle_dir/fake-mv"
mkdir -p "$fake_mv_dir"
fake_mv_state="$bundle_dir/fake-mv-failed"
cat > "$fake_mv_dir/mv" <<'FAKE_MV'
#!/usr/bin/env bash
set -euo pipefail

last=''
for argument in "$@"; do
    last="$argument"
done
if [[ -n "${FAKE_MV_SIGNAL_STATE:-}" && "$last" == *.backup.* &&
    ! -f "$FAKE_MV_SIGNAL_STATE" ]]; then
    : > "$FAKE_MV_SIGNAL_STATE"
    kill -TERM "$PPID"
fi
if [[ "$last" == "${FAKE_MV_FAIL_TARGET:-}" ]]; then
    failure_count=0
    if [[ -f "$FAKE_MV_STATE" ]]; then
        failure_count="$(< "$FAKE_MV_STATE")"
    fi
    if ((failure_count < ${FAKE_MV_FAILURE_COUNT:-1})); then
        printf '%s\n' "$((failure_count + 1))" > "$FAKE_MV_STATE"
        printf 'Intentional atomic replacement failure for %s\n' "$last" >&2
        exit 97
    fi
fi
exec /bin/mv "$@"
FAKE_MV
chmod +x "$fake_mv_dir/mv"

canonical_import_destination="$(CDPATH= cd -- "$full_fake_import_destination" && pwd -P)"
install_failure_snapshot="$bundle_dir/install-failure-snapshot"
snapshot_five_file_bundle "$full_fake_import_destination" "$install_failure_snapshot"
PATH="$fake_mv_dir:$PATH" \
    FAKE_MV_FAIL_TARGET="$canonical_import_destination" \
    FAKE_MV_STATE="$fake_mv_state" \
    FAKE_CAPTURE_TAG=rollback-new \
    expect_failure run_full_fake_capture --import
if [[ "$(< "$full_fake_import_destination/uniform-light.png")" != "$import_uniform_before_failure" ]]; then
    printf 'Atomic import failure did not restore the previous destination\n' >&2
    exit 1
fi
assert_bundle_matches_snapshot \
    "$full_fake_import_destination" \
    "$install_failure_snapshot" \
    'Install rename failure'

restore_failure_parent="$bundle_dir/restore-failure-parent"
restore_failure_destination="$restore_failure_parent/ios26"
mkdir "$restore_failure_parent"
snapshot_five_file_bundle "$full_fake_import_destination" "$restore_failure_destination"
canonical_restore_failure_destination="$(CDPATH= cd -- "$restore_failure_destination" && pwd -P)"
restore_failure_state="$bundle_dir/restore-failure-state"
PATH="$fake_mv_dir:$PATH" \
    TEST_IMPORT_DESTINATION="$restore_failure_destination" \
    FAKE_MV_FAIL_TARGET="$canonical_restore_failure_destination" \
    FAKE_MV_FAILURE_COUNT=2 \
    FAKE_MV_STATE="$restore_failure_state" \
    FAKE_CAPTURE_TAG=restore-failure \
    expect_failure run_full_fake_capture --import
restore_failure_backup=''
for candidate in "$restore_failure_parent/.ios26.backup."*; do
    if [[ -d "$candidate" ]]; then
        restore_failure_backup="$candidate"
    fi
done
if [[ -z "$restore_failure_backup" || -e "$restore_failure_destination" ]]; then
    printf 'Failed restore did not retain only the previous bundle backup\n' >&2
    exit 1
fi
assert_bundle_matches_snapshot \
    "$restore_failure_backup" \
    "$full_fake_import_destination" \
    'Failed restore backup'
if [[ -d "$full_fake_lock_dir" ]]; then
    printf 'Failed restore leaked capture lock\n' >&2
    exit 1
fi
for leftover in "$restore_failure_parent/.ios26.import."*; do
    if [[ -e "$leftover" ]]; then
        printf 'Failed restore leaked import staging: %s\n' "$leftover" >&2
        exit 1
    fi
done

signal_state="$bundle_dir/rename-signal-state"
PATH="$fake_mv_dir:$PATH" \
    FAKE_MV_SIGNAL_STATE="$signal_state" \
    FAKE_CAPTURE_TAG=rename-signal \
    expect_success run_full_fake_capture --import
if [[ ! -f "$signal_state" ||
    "$(< "$full_fake_import_destination/uniform-light.png")" != *'rename-signal'* ]]; then
    printf 'Signal during atomic rename interrupted the completed import\n' >&2
    exit 1
fi

import_parent="$(dirname "$full_fake_import_destination")"
import_name="$(basename "$full_fake_import_destination")"
for leftover in \
    "$import_parent/.$import_name.backup."* \
    "$import_parent/.$import_name.import."*; do
    if [[ -e "$leftover" ]]; then
        printf 'Atomic import left temporary path: %s\n' "$leftover" >&2
        exit 1
    fi
done

mkdir "$full_fake_lock_dir"
printf '4242\n' > "$full_fake_lock_dir/pid"
xcrun_log_before_lock_test="$(< "$full_fake_xcrun_log")"
if lock_output="$(run_full_fake_capture 2>&1)"; then
    printf 'Expected concurrent capture invocation to fail on held lock\n' >&2
    exit 1
fi
if [[ "$lock_output" != *'4242'* ]]; then
    printf 'Held-lock failure did not report owning PID:\n%s\n' "$lock_output" >&2
    exit 1
fi
if [[ "$(< "$full_fake_xcrun_log")" != "$xcrun_log_before_lock_test" ]]; then
    printf 'Concurrent invocation mutated simulator state while lock was held\n' >&2
    exit 1
fi
if [[ ! -d "$full_fake_lock_dir" ]]; then
    printf 'Concurrent invocation removed a lock it did not own\n' >&2
    exit 1
fi
/bin/rm -f "$full_fake_lock_dir/pid"
rmdir "$full_fake_lock_dir"

xcrun_log_before_wrong_xcode="$(< "$full_fake_xcrun_log")"
if wrong_xcode_output="$(
    FAKE_XCODE_VERSION='Xcode 26.2\nBuild version WRONG\n' \
        run_full_fake_capture 2>&1
)"; then
    printf 'Expected capture to reject non-Xcode-26.3 producer\n' >&2
    exit 1
fi
if [[ "$wrong_xcode_output" != *'Xcode 26.3 is required'* ]]; then
    printf 'Wrong-Xcode failure was unclear:\n%s\n' "$wrong_xcode_output" >&2
    exit 1
fi
if [[ "$(< "$full_fake_xcrun_log")" != "$xcrun_log_before_wrong_xcode" ]]; then
    printf 'Wrong-Xcode capture touched simulator before provenance rejection\n' >&2
    exit 1
fi
if [[ -d "$full_fake_lock_dir" ]]; then
    printf 'Wrong-Xcode failure leaked capture lock\n' >&2
    exit 1
fi

xcrun_log_before_dirty_repo="$(< "$full_fake_xcrun_log")"
if dirty_repo_output="$(FAKE_GIT_DIRTY=1 run_full_fake_capture 2>&1)"; then
    printf 'Expected capture to reject dirty producer repository\n' >&2
    exit 1
fi
if [[ "$dirty_repo_output" != *'worktree must be clean'* ||
    "$dirty_repo_output" != *'UntrackedCaptureSource.swift'* ]]; then
    printf 'Dirty-repository failure was unclear:\n%s\n' "$dirty_repo_output" >&2
    exit 1
fi
if [[ "$(< "$full_fake_xcrun_log")" != "$xcrun_log_before_dirty_repo" ]]; then
    printf 'Dirty-repository capture touched simulator before provenance rejection\n' >&2
    exit 1
fi
if [[ -d "$full_fake_lock_dir" ]]; then
    printf 'Dirty-repository failure leaked capture lock\n' >&2
    exit 1
fi

current_uniform_before_bad_frame="$(< "$full_fake_capture_root/current/uniform-light.png")"
import_uniform_before_bad_frame="$(< "$full_fake_import_destination/uniform-light.png")"
if bad_frame_output="$(
    FAKE_FRAMEBUFFER_WIDTH=1205 \
        FAKE_CAPTURE_TAG=bad-frame \
        run_full_fake_capture --import 2>&1
)"; then
    printf 'Expected capture to reject wrong full-frame screenshot dimensions\n' >&2
    exit 1
fi
if [[ "$bad_frame_output" != *'Full-frame screenshot'* ||
    "$bad_frame_output" != *'1205x2622'* ]]; then
    printf 'Wrong-framebuffer failure was unclear:\n%s\n' "$bad_frame_output" >&2
    exit 1
fi
if [[ "$(< "$full_fake_capture_root/current/uniform-light.png")" != \
    "$current_uniform_before_bad_frame" ]]; then
    printf 'Wrong full-frame screenshot replaced prior current staging\n' >&2
    exit 1
fi
if [[ "$(< "$full_fake_import_destination/uniform-light.png")" != \
    "$import_uniform_before_bad_frame" ]]; then
    printf 'Wrong full-frame screenshot modified import destination\n' >&2
    exit 1
fi
if [[ -d "$full_fake_lock_dir" ]]; then
    printf 'Wrong-framebuffer failure leaked capture lock\n' >&2
    exit 1
fi

timeout_started=$SECONDS
if timeout_output="$(
    CAPTURE_COMMAND_TIMEOUT_SECONDS=1 \
        FAKE_HANG_XCODE_VERSION=1 \
        run_full_fake_capture 2>&1
)"; then
    printf 'Expected hung Xcode version query to time out\n' >&2
    exit 1
fi
timeout_elapsed=$((SECONDS - timeout_started))
if ((timeout_elapsed > 3)); then
    printf 'Hung operation timeout took too long: %s seconds\n' "$timeout_elapsed" >&2
    exit 1
fi
if [[ "$timeout_output" != *'timed out after 1 seconds'* ||
    "$timeout_output" != *'Xcode producer version'* ]]; then
    printf 'Hung-operation timeout was unclear:\n%s\n' "$timeout_output" >&2
    exit 1
fi
if ! mkdir "$full_fake_lock_dir"; then
    printf 'Hung-operation timeout did not release capture lock\n' >&2
    exit 1
fi
rmdir "$full_fake_lock_dir"

provenance_mutation_marker="$bundle_dir/provenance-mutated-after-build"
current_uniform_before_provenance_mutation="$(< "$full_fake_capture_root/current/uniform-light.png")"
current_manifest_before_provenance_mutation="$(< "$full_fake_capture_root/current/manifest.json")"
import_uniform_before_provenance_mutation="$(< "$full_fake_import_destination/uniform-light.png")"
if provenance_mutation_output="$(
    FAKE_MUTATE_AFTER_BUILD_MARKER="$provenance_mutation_marker" \
        FAKE_CAPTURE_TAG=provenance-mutated \
        run_full_fake_capture --import 2>&1
)"; then
    printf 'Expected capture to reject repository mutation after initial provenance check\n' >&2
    exit 1
fi
if [[ "$provenance_mutation_output" != *'provenance changed during capture'* ||
    "$provenance_mutation_output" != *'ReferenceSceneView.swift'* ]]; then
    printf 'Mid-capture provenance failure was unclear:\n%s\n' \
        "$provenance_mutation_output" >&2
    exit 1
fi
if [[ "$(< "$full_fake_capture_root/current/uniform-light.png")" != \
    "$current_uniform_before_provenance_mutation" ||
    "$(< "$full_fake_capture_root/current/manifest.json")" != \
        "$current_manifest_before_provenance_mutation" ]]; then
    printf 'Mid-capture provenance mutation replaced prior current bundle\n' >&2
    exit 1
fi
if [[ "$(< "$full_fake_import_destination/uniform-light.png")" != \
    "$import_uniform_before_provenance_mutation" ]]; then
    printf 'Mid-capture provenance mutation modified import destination\n' >&2
    exit 1
fi
if [[ -d "$full_fake_lock_dir" ]]; then
    printf 'Mid-capture provenance failure leaked capture lock\n' >&2
    exit 1
fi
/bin/rm -f "$provenance_mutation_marker"

import_status_count_file="$bundle_dir/import-provenance-status-count"
import_uniform_before_final_provenance_check="$(< "$full_fake_import_destination/uniform-light.png")"
if import_provenance_output="$(
    FAKE_GIT_STATUS_COUNT_FILE="$import_status_count_file" \
        FAKE_GIT_DIRTY_ON_STATUS_CALL=3 \
        FAKE_CAPTURE_TAG=import-provenance-mutated \
        run_full_fake_capture --import 2>&1
)"; then
    printf 'Expected import to re-check provenance immediately before replacement\n' >&2
    exit 1
fi
if [[ "$import_provenance_output" != *'provenance changed during capture'* ||
    "$import_provenance_output" != *'CaptureMetadata.swift'* ]]; then
    printf 'Pre-import provenance failure was unclear:\n%s\n' \
        "$import_provenance_output" >&2
    exit 1
fi
if [[ "$(< "$full_fake_import_destination/uniform-light.png")" != \
    "$import_uniform_before_final_provenance_check" ]]; then
    printf 'Pre-import provenance failure modified import destination\n' >&2
    exit 1
fi
if [[ -d "$full_fake_lock_dir" ]]; then
    printf 'Pre-import provenance failure leaked capture lock\n' >&2
    exit 1
fi

fake_repo_root="$bundle_dir/fake repo root"
fake_import_parent_relative='haze-screenshot-tests/src/commonTest/resources/glass'
fake_repo_import_destination="$fake_repo_root/$fake_import_parent_relative/ios26"
mkdir -p "$fake_repo_import_destination"
for expected_file in \
    manifest.json \
    uniform-light.png \
    uniform-dark.png \
    grid-light.png \
    grid-dark.png; do
    cp "$full_fake_import_destination/$expected_file" \
        "$fake_repo_import_destination/$expected_file"
done
fake_repo_revision="$(git -C "$capture_tool_dir" rev-parse HEAD)"

TEST_REPO_ROOT="$fake_repo_root" \
    TEST_IMPORT_DESTINATION="$fake_repo_import_destination" \
    FAKE_REPO_ROOT="$fake_repo_root" \
    FAKE_GIT_REVISION="$fake_repo_revision" \
    FAKE_IMPORT_PARENT_RELATIVE="$fake_import_parent_relative" \
    FAKE_REPORT_ACTIVE_IMPORT_TEMP=1 \
    FAKE_CAPTURE_TAG=self-owned-import-temp \
    expect_success run_full_fake_capture --import

if [[ "$(< "$fake_repo_import_destination/uniform-light.png")" != \
    *'self-owned-import-temp'* ]]; then
    printf 'Import with script-owned sibling temp did not replace destination\n' >&2
    exit 1
fi

fake_repo_uniform_before_external_untracked="$(< "$fake_repo_import_destination/uniform-light.png")"
fake_repo_status_count="$bundle_dir/fake-repo-status-count"
if external_untracked_output="$(
    TEST_REPO_ROOT="$fake_repo_root" \
        TEST_IMPORT_DESTINATION="$fake_repo_import_destination" \
        FAKE_REPO_ROOT="$fake_repo_root" \
        FAKE_GIT_REVISION="$fake_repo_revision" \
        FAKE_IMPORT_PARENT_RELATIVE="$fake_import_parent_relative" \
        FAKE_REPORT_ACTIVE_IMPORT_TEMP=1 \
        FAKE_GIT_STATUS_COUNT_FILE="$fake_repo_status_count" \
        FAKE_EXTRA_UNTRACKED_AT_STATUS_CALL=3 \
        FAKE_CAPTURE_TAG=external-untracked \
        run_full_fake_capture --import 2>&1
)"; then
    printf 'Expected similarly prefixed external untracked file to block import\n' >&2
    exit 1
fi
if [[ "$external_untracked_output" != *'.ios26.import.external-note.txt'* ]]; then
    printf 'External untracked sibling failure was unclear:\n%s\n' \
        "$external_untracked_output" >&2
    exit 1
fi
if [[ "$(< "$fake_repo_import_destination/uniform-light.png")" != \
    "$fake_repo_uniform_before_external_untracked" ]]; then
    printf 'External untracked sibling modified original import destination\n' >&2
    exit 1
fi
if [[ -d "$full_fake_lock_dir" ]]; then
    printf 'External untracked sibling failure leaked capture lock\n' >&2
    exit 1
fi
