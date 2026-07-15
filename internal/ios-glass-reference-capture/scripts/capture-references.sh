#!/usr/bin/env bash
set -euo pipefail

XCRUN=${XCRUN:-xcrun}
XCODEBUILD=${XCODEBUILD:-xcodebuild}
JQ=${JQ:-jq}
SIPS=${SIPS:-sips}
GIT=${GIT:-git}
readonly XCRUN XCODEBUILD JQ SIPS GIT
readonly RUNTIME_ID=com.apple.CoreSimulator.SimRuntime.iOS-26-3
readonly DEVICE_TYPE_ID=com.apple.CoreSimulator.SimDeviceType.iPhone-17
readonly DEVICE_NAME='Haze Glass Reference'
readonly BUNDLE_ID=dev.chrisbanes.haze.glassreferencecapture

script_dir="$(CDPATH= cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
readonly script_dir
tool_dir="$(CDPATH= cd -- "$script_dir/.." && pwd -P)"
readonly tool_dir
if [[ -z "${REPO_ROOT:-}" ]]; then
    if ! command -v "$GIT" >/dev/null 2>&1; then
        printf 'capture-references.sh: Required command not found: %s\n' "$GIT" >&2
        exit 1
    fi
    if ! REPO_ROOT="$("$GIT" -C "$tool_dir" rev-parse --show-toplevel)"; then
        printf 'capture-references.sh: Unable to resolve repository root from %s\n' \
            "$tool_dir" >&2
        exit 1
    fi
fi
readonly REPO_ROOT
IMPORT_DESTINATION=${IMPORT_DESTINATION:-$REPO_ROOT/haze-screenshot-tests/src/commonTest/resources/glass/ios26}
readonly IMPORT_DESTINATION
CAPTURE_ROOT=${CAPTURE_ROOT:-$tool_dir/build/captures}
readonly CAPTURE_ROOT
DERIVED_DATA_DIR=${DERIVED_DATA_DIR:-$tool_dir/build/DerivedData}
readonly DERIVED_DATA_DIR
CAPTURE_LOCK_DIR=${CAPTURE_LOCK_DIR:-$tool_dir/build/.capture-references.lock}
readonly CAPTURE_LOCK_DIR
BOOT_TIMEOUT_SECONDS=${CAPTURE_BOOT_TIMEOUT_SECONDS:-120}
BUILD_TIMEOUT_SECONDS=${CAPTURE_BUILD_TIMEOUT_SECONDS:-600}
COMMAND_TIMEOUT_SECONDS=${CAPTURE_COMMAND_TIMEOUT_SECONDS:-60}
readonly BOOT_TIMEOUT_SECONDS BUILD_TIMEOUT_SECONDS COMMAND_TIMEOUT_SECONDS

readonly VIEWPORT_WIDTH=1080
readonly VIEWPORT_HEIGHT=2160
readonly FRAMEBUFFER_WIDTH=1206
readonly FRAMEBUFFER_HEIGHT=2622
readonly CAPTURE_SCENES=(
    uniform-light
    uniform-dark
    grid-light
    grid-dark
)
readonly SCENE_NAMES=(
    grid-dark.png
    grid-light.png
    uniform-dark.png
    uniform-light.png
)
readonly SURFACE_NAMES=(
    capsule
    card
    panel
)

capture_staging_path=''
import_staging_path=''
lock_owned=0
lock_owner_recorded=0
active_command_pid=''
active_output_path=''
timed_command_output=''
png_property_value=''
pinned_revision=''
worktree_status=''

terminate_process_group() {
    local pid="$1"
    local signal="$2"

    kill -"$signal" -- "-$pid" 2>/dev/null || kill -"$signal" "$pid" 2>/dev/null || true
}

terminate_active_command() {
    if [[ -n "$active_command_pid" ]]; then
        terminate_process_group "$active_command_pid" TERM
        sleep 0.2
        terminate_process_group "$active_command_pid" KILL
        wait "$active_command_pid" 2>/dev/null || true
        active_command_pid=''
    fi
    if [[ -n "$active_output_path" && -e "$active_output_path" ]]; then
        rm -f "$active_output_path"
        active_output_path=''
    fi
}

release_capture_lock() {
    local recorded_pid

    if [[ "$lock_owned" -ne 1 ]]; then
        return 0
    fi

    if [[ "$lock_owner_recorded" -eq 1 ]]; then
        if [[ ! -f "$CAPTURE_LOCK_DIR/pid" ]]; then
            printf 'capture-references.sh: WARNING: refusing to release lock with missing ownership record: %s\n' \
                "$CAPTURE_LOCK_DIR" >&2
            lock_owned=0
            return 1
        fi
        recorded_pid="$(< "$CAPTURE_LOCK_DIR/pid")"
        if [[ "$recorded_pid" != "$$" ]]; then
            printf 'capture-references.sh: WARNING: refusing to release lock now owned by PID %s: %s\n' \
                "$recorded_pid" "$CAPTURE_LOCK_DIR" >&2
            lock_owned=0
            return 1
        fi
    fi

    rm -f "$CAPTURE_LOCK_DIR/pid"
    if ! rmdir "$CAPTURE_LOCK_DIR"; then
        printf 'capture-references.sh: WARNING: unable to release owned capture lock: %s\n' \
            "$CAPTURE_LOCK_DIR" >&2
        return 1
    fi
    lock_owned=0
    lock_owner_recorded=0
}

cleanup_on_exit() {
    local exit_status=$?

    # Cleanup is a single-entry critical section. Preserve the triggering status, disable EXIT
    # reentry, and ignore subsequent termination signals until cleanup and lock release finish.
    trap - EXIT
    trap '' INT TERM HUP

    # EXIT traps are inherited by command-substitution subshells on Apple Bash. Only the top-level
    # script owns capture state and the lock.
    if ((BASH_SUBSHELL > 0)); then
        return "$exit_status"
    fi

    set +e
    terminate_active_command
    if [[ -n "$capture_staging_path" && -e "$capture_staging_path" ]]; then
        rm -rf "$capture_staging_path"
    fi
    if [[ -n "$import_staging_path" && -e "$import_staging_path" ]]; then
        rm -rf "$import_staging_path"
    fi
    release_capture_lock
    exit "$exit_status"
}

install_termination_traps() {
    trap 'exit 129' HUP
    trap 'exit 130' INT
    trap 'exit 143' TERM
}

trap cleanup_on_exit EXIT
install_termination_traps

fail() {
    printf 'capture-references.sh: %s\n' "$*" >&2
    return 1
}

require_command() {
    local command_path="$1"

    if ! command -v "$command_path" >/dev/null 2>&1; then
        fail "Required command not found: $command_path"
        return 1
    fi
}

run_with_timeout() {
    local timeout_seconds="$1"
    local description="$2"
    local command_status
    local elapsed_ticks=0
    local grace_ticks
    local max_ticks
    shift 2

    case "$timeout_seconds" in
        ''|*[!0-9]*|0)
            fail "Timeout for $description must be a positive integer, found: $timeout_seconds"
            return 1
            ;;
    esac

    # Monitor mode gives the background command its own process group. This lets timeout and signal
    # cleanup terminate the complete command tree rather than orphaning grandchildren.
    set -m
    "$@" &
    active_command_pid=$!
    set +m
    max_ticks=$((timeout_seconds * 10))

    while kill -0 "$active_command_pid" 2>/dev/null; do
        if ((elapsed_ticks >= max_ticks)); then
            terminate_process_group "$active_command_pid" TERM
            grace_ticks=0
            while kill -0 "$active_command_pid" 2>/dev/null && ((grace_ticks < 10)); do
                sleep 0.1
                grace_ticks=$((grace_ticks + 1))
            done
            terminate_process_group "$active_command_pid" KILL
            wait "$active_command_pid" 2>/dev/null || true
            active_command_pid=''
            fail "$description timed out after $timeout_seconds seconds"
            return 124
        fi
        sleep 0.1
        elapsed_ticks=$((elapsed_ticks + 1))
    done

    set +e
    wait "$active_command_pid"
    command_status=$?
    set -e
    active_command_pid=''
    return "$command_status"
}

run_with_timeout_capture() {
    local timeout_seconds="$1"
    local description="$2"
    local command_status
    shift 2

    active_output_path="${TMPDIR:-/tmp}/capture-references-output.$$.$RANDOM"
    if [[ -e "$active_output_path" ]]; then
        fail "Command output path already exists: $active_output_path"
        return 1
    fi

    set +e
    run_with_timeout "$timeout_seconds" "$description" "$@" > "$active_output_path"
    command_status=$?
    set -e
    timed_command_output="$(< "$active_output_path")"
    rm -f "$active_output_path"
    active_output_path=''
    return "$command_status"
}

acquire_capture_lock() {
    local lock_parent
    local owner_pid='unknown'

    lock_parent="$(dirname "$CAPTURE_LOCK_DIR")"
    mkdir -p "$lock_parent"
    if ! mkdir "$CAPTURE_LOCK_DIR" 2>/dev/null; then
        if [[ -f "$CAPTURE_LOCK_DIR/pid" ]]; then
            owner_pid="$(< "$CAPTURE_LOCK_DIR/pid")"
        fi
        fail "Another capture run holds lock $CAPTURE_LOCK_DIR (PID $owner_pid); stale locks require manual investigation"
        return 1
    fi

    lock_owned=1
    if ! printf '%s\n' "$$" > "$CAPTURE_LOCK_DIR/pid"; then
        fail "Unable to record capture lock owner: $CAPTURE_LOCK_DIR"
        return 1
    fi
    lock_owner_recorded=1
}

read_provenance_worktree_status() {
    local active_import_relative=''
    local canonical_repo_root
    local canonical_import_staging
    local command_status
    local path
    local record

    canonical_repo_root="$(CDPATH= cd -- "$REPO_ROOT" && pwd -P)"
    if [[ -n "$import_staging_path" && -d "$import_staging_path" ]]; then
        canonical_import_staging="$(CDPATH= cd -- "$import_staging_path" && pwd -P)"
        case "$canonical_import_staging" in
            "$canonical_repo_root"/*)
                active_import_relative="${canonical_import_staging#"$canonical_repo_root/"}"
                ;;
        esac
    fi

    active_output_path="${TMPDIR:-/tmp}/capture-references-status.$$.$RANDOM"
    if [[ -e "$active_output_path" ]]; then
        fail "Git status output path already exists: $active_output_path"
        return 1
    fi

    set +e
    run_with_timeout \
        "$COMMAND_TIMEOUT_SECONDS" \
        'producer repository status query' \
        "$GIT" -C "$REPO_ROOT" status --porcelain=v1 -z --untracked-files=all \
        > "$active_output_path"
    command_status=$?
    set -e
    if [[ "$command_status" -ne 0 ]]; then
        rm -f "$active_output_path"
        active_output_path=''
        return "$command_status"
    fi

    worktree_status=''
    while IFS= read -r -d '' record; do
        if [[ "${record:0:3}" == '?? ' ]]; then
            path="${record:3}"
            if [[ -n "$active_import_relative" &&
                ("$path" == "$active_import_relative" ||
                    "$path" == "$active_import_relative/"*) ]]; then
                continue
            fi
        fi

        if [[ -n "$worktree_status" ]]; then
            worktree_status="$worktree_status
$record"
        else
            worktree_status="$record"
        fi
    done < "$active_output_path"

    rm -f "$active_output_path"
    active_output_path=''
}

verify_capture_provenance() {
    local xcode_first_line
    local xcode_remaining_lines

    if ! run_with_timeout_capture \
        "$COMMAND_TIMEOUT_SECONDS" \
        'Xcode producer version query' \
        "$XCODEBUILD" -version; then
        fail 'Unable to read Xcode producer version'
        return 1
    fi
    producer_xcode_version="$timed_command_output"
    xcode_first_line="${producer_xcode_version%%$'\n'*}"
    if [[ "$producer_xcode_version" == *$'\n'* ]]; then
        xcode_remaining_lines="${producer_xcode_version#*$'\n'}"
    else
        xcode_remaining_lines=''
    fi
    if [[ "$xcode_first_line" != 'Xcode 26.3' ||
        "$xcode_remaining_lines" != 'Build version '* ]]; then
        fail "Xcode 26.3 is required; found: ${producer_xcode_version//$'\n'/; }"
        return 1
    fi

    if ! run_with_timeout_capture \
        "$COMMAND_TIMEOUT_SECONDS" \
        'initial producer revision query' \
        "$GIT" -C "$REPO_ROOT" rev-parse HEAD; then
        fail "Unable to pin producer revision from repository: $REPO_ROOT"
        return 1
    fi
    pinned_revision="$timed_command_output"
    if [[ -z "$pinned_revision" ]]; then
        fail 'Pinned producer revision is empty'
        return 1
    fi

    if ! read_provenance_worktree_status; then
        fail "Unable to inspect producer repository worktree: $REPO_ROOT"
        return 1
    fi
    if [[ -n "$worktree_status" ]]; then
        printf 'capture-references.sh: Producer repository worktree must be clean, including untracked files:\n' >&2
        printf '%s\n' "$worktree_status" >&2
        return 1
    fi
}

verify_pinned_provenance() {
    local current_revision

    if ! run_with_timeout_capture \
        "$COMMAND_TIMEOUT_SECONDS" \
        'current producer revision query' \
        "$GIT" -C "$REPO_ROOT" rev-parse HEAD; then
        fail "Unable to re-check producer revision from repository: $REPO_ROOT"
        return 1
    fi
    current_revision="$timed_command_output"
    if [[ "$current_revision" != "$pinned_revision" ]]; then
        fail "capture provenance changed during capture: HEAD is $current_revision, pinned $pinned_revision"
        return 1
    fi

    if ! read_provenance_worktree_status; then
        fail "Unable to re-check producer repository worktree: $REPO_ROOT"
        return 1
    fi
    if [[ -n "$worktree_status" ]]; then
        printf 'capture-references.sh: capture provenance changed during capture; worktree is no longer clean:\n' >&2
        printf '%s\n' "$worktree_status" >&2
        return 1
    fi
}

usage() {
    printf 'Usage: %s [--import | --validate-only DIRECTORY]\n' "${0##*/}" >&2
}

read_png_property() {
    local png_path="$1"
    local property="$2"
    local output
    local line
    local key
    local value

    if ! run_with_timeout_capture \
        "$COMMAND_TIMEOUT_SECONDS" \
        "reading PNG $property" \
        "$SIPS" -g "$property" "$png_path"; then
        fail "Unable to read PNG $property for $png_path"
        return 1
    fi
    output="$timed_command_output"

    while IFS= read -r line; do
        key="${line%%:*}"
        key="${key//[[:space:]]/}"
        if [[ "$key" == "$property" ]]; then
            value="${line#*:}"
            value="${value#"${value%%[![:space:]]*}"}"
            value="${value%"${value##*[![:space:]]}"}"
            if [[ -n "$value" ]]; then
                png_property_value="$value"
                return 0
            fi
        fi
    done <<< "$output"

    fail "PNG $png_path does not provide $property"
    return 1
}

validate_manifest() {
    local manifest_path="$1"
    local surface
    local x
    local y
    local width
    local height

    if ! "$JQ" -e '
        .platform == "iOS 26" and
        (.osBuild | type == "string" and length > 0) and
        (.device | type == "string" and length > 0) and
        (.scale | type == "number" and . > 0) and
        (.colorSpace | type == "string" and length > 0) and
        .material == "Regular" and
        .tint == "transparent" and
        ([.scenes | keys[]] | sort) == (["grid-dark.png", "grid-light.png", "uniform-dark.png", "uniform-light.png"] | sort) and
        ([.surfaces | keys[]] | sort) == (["capsule", "card", "panel"] | sort) and
        ([.surfaces[] | .x, .y, .width, .height] | all(type == "number"))
    ' "$manifest_path" >/dev/null; then
        fail "Manifest does not satisfy the rendering-plan contract"
        return 1
    fi

    if ! "$JQ" -e '
        .schemaVersion == 1 and
        .device == "iPhone 17" and
        .scale == 3 and
        .scenes == {
            "uniform-light.png": { appearance: "light", background: "uniform" },
            "uniform-dark.png": { appearance: "dark", background: "uniform" },
            "grid-light.png": { appearance: "light", background: "grid" },
            "grid-dark.png": { appearance: "dark", background: "grid" }
        } and
        (.producer.xcode | type == "string" and length > 0) and
        (.producer.runtime | type == "string" and length > 0) and
        (.producer.revision | type == "string" and length > 0) and
        all(.surfaces[]; .x >= 0 and .y >= 0 and .width > 0 and .height > 0) and
        .surfaces.capsule == { x: 180, y: 270, width: 720, height: 192 } and
        .surfaces.card == { x: 120, y: 672, width: 840, height: 528 } and
        .surfaces.panel == { x: 60, y: 1380, width: 960, height: 660 }
    ' "$manifest_path" >/dev/null; then
        fail "Manifest has invalid metadata or surface geometry"
        return 1
    fi

    for surface in "${SURFACE_NAMES[@]}"; do
        x="$("$JQ" -er --arg surface "$surface" '.surfaces[$surface].x' "$manifest_path")"
        y="$("$JQ" -er --arg surface "$surface" '.surfaces[$surface].y' "$manifest_path")"
        width="$("$JQ" -er --arg surface "$surface" '.surfaces[$surface].width' "$manifest_path")"
        height="$("$JQ" -er --arg surface "$surface" '.surfaces[$surface].height' "$manifest_path")"

        if ((x + width > VIEWPORT_WIDTH || y + height > VIEWPORT_HEIGHT)); then
            fail "Surface $surface is outside the ${VIEWPORT_WIDTH}x${VIEWPORT_HEIGHT} viewport"
            return 1
        fi
    done
}

validate_bundle() {
    local requested_directory="$1"
    local bundle_directory
    local manifest_path
    local scene
    local png_path
    local width
    local height
    local image_format
    local color_space
    local common_color_space=""
    local manifest_color_space

    if [[ ! -d "$requested_directory" ]]; then
        fail "Bundle directory does not exist: $requested_directory"
        return 1
    fi

    require_command "$JQ"
    require_command "$SIPS"

    bundle_directory="$(CDPATH= cd -- "$requested_directory" && pwd -P)"
    manifest_path="$bundle_directory/manifest.json"
    if [[ ! -f "$manifest_path" ]]; then
        fail "Bundle is missing manifest.json: $bundle_directory"
        return 1
    fi

    for scene in "${SCENE_NAMES[@]}"; do
        png_path="$bundle_directory/$scene"
        if [[ ! -f "$png_path" ]]; then
            fail "Bundle is missing required PNG: $scene"
            return 1
        fi
    done

    validate_manifest "$manifest_path"

    for scene in "${SCENE_NAMES[@]}"; do
        png_path="$bundle_directory/$scene"
        read_png_property "$png_path" format
        image_format="$png_property_value"
        read_png_property "$png_path" pixelWidth
        width="$png_property_value"
        read_png_property "$png_path" pixelHeight
        height="$png_property_value"
        read_png_property "$png_path" space
        color_space="$png_property_value"

        if [[ "$image_format" != 'png' ]]; then
            fail "Image $scene must be PNG, found $image_format"
            return 1
        fi

        if [[ "$width" != "$VIEWPORT_WIDTH" || "$height" != "$VIEWPORT_HEIGHT" ]]; then
            fail "PNG $scene must be ${VIEWPORT_WIDTH}x${VIEWPORT_HEIGHT}, found ${width}x${height}"
            return 1
        fi

        if [[ -z "$common_color_space" ]]; then
            common_color_space="$color_space"
        elif [[ "$color_space" != "$common_color_space" ]]; then
            fail "PNG $scene color space $color_space does not match $common_color_space"
            return 1
        fi
    done

    if [[ "$common_color_space" != *RGB* ]]; then
        fail "PNG color space must contain RGB, found $common_color_space"
        return 1
    fi

    manifest_color_space="$("$JQ" -er '.colorSpace' "$manifest_path")"
    if [[ "$manifest_color_space" != "$common_color_space" ]]; then
        fail "Manifest color space $manifest_color_space does not match PNG color space $common_color_space"
        return 1
    fi

    printf 'Validated iOS Glass reference bundle: %s\n' "$bundle_directory"
}

select_simulator() {
    local runtime_json
    local device_json
    local runtime_metadata

    if ! run_with_timeout_capture \
        "$COMMAND_TIMEOUT_SECONDS" \
        'simulator runtime query' \
        "$XCRUN" simctl list runtimes --json; then
        fail 'Unable to query CoreSimulator runtimes'
        return 1
    fi
    runtime_json="$timed_command_output"
    if ! runtime_metadata="$(
        printf '%s\n' "$runtime_json" | "$JQ" -er --arg runtime_id "$RUNTIME_ID" '
            [.runtimes[] | select(.identifier == $runtime_id and .isAvailable == true)] |
            if length == 1 then
                .[0] |
                if all([.identifier, .name, .version, .buildversion][];
                    type == "string" and length > 0)
                then
                    [.identifier, .name, .version, .buildversion] | @tsv
                else
                    error("runtime metadata is incomplete")
                end
            else
                error("required available runtime was not found")
            end
        '
    )"; then
        fail "Required available simulator runtime not found: $RUNTIME_ID"
        return 1
    fi
    IFS=$'\t' read -r \
        selected_runtime_identifier \
        selected_runtime_name \
        selected_runtime_version \
        selected_runtime_build <<< "$runtime_metadata"

    if ! run_with_timeout_capture \
        "$COMMAND_TIMEOUT_SECONDS" \
        'simulator device query' \
        "$XCRUN" simctl list devices --json; then
        fail 'Unable to query CoreSimulator devices'
        return 1
    fi
    device_json="$timed_command_output"
    selected_device_udid="$(
        printf '%s\n' "$device_json" | "$JQ" -er \
            --arg runtime_id "$RUNTIME_ID" \
            --arg device_name "$DEVICE_NAME" \
            --arg device_type_id "$DEVICE_TYPE_ID" '
                [.devices[$runtime_id][]? |
                    select(
                        .name == $device_name and
                        .deviceTypeIdentifier == $device_type_id and
                        .isAvailable == true
                    )] |
                sort_by(.udid) |
                if length == 0 then "" else .[0].udid end
            '
    )"

    if [[ -z "$selected_device_udid" ]]; then
        if ! run_with_timeout_capture \
            "$COMMAND_TIMEOUT_SECONDS" \
            'dedicated simulator creation' \
            "$XCRUN" simctl create "$DEVICE_NAME" "$DEVICE_TYPE_ID" "$RUNTIME_ID"; then
            fail "Unable to create dedicated simulator: $DEVICE_NAME"
            return 1
        fi
        selected_device_udid="$timed_command_output"
    fi

    if ! run_with_timeout "$COMMAND_TIMEOUT_SECONDS" 'simulator boot request' \
        "$XCRUN" simctl boot "$selected_device_udid"; then
        if ! run_with_timeout_capture \
            "$COMMAND_TIMEOUT_SECONDS" \
            'booted simulator state query' \
            "$XCRUN" simctl list devices --json; then
            fail "Unable to verify booted simulator after boot request: $selected_device_udid"
            return 1
        fi
        device_json="$timed_command_output"
        if ! printf '%s\n' "$device_json" | "$JQ" -e \
            --arg udid "$selected_device_udid" \
            'any(.devices[][]?; .udid == $udid and .state == "Booted")' >/dev/null; then
            fail "Unable to boot simulator: $selected_device_udid"
            return 1
        fi
    fi
    if ! run_with_timeout "$BOOT_TIMEOUT_SECONDS" 'simulator bootstatus' \
        "$XCRUN" simctl bootstatus "$selected_device_udid" -b; then
        fail "Simulator did not finish booting: $selected_device_udid"
        return 1
    fi
}

build_and_install_app() {
    local project_path="$tool_dir/GlassReferenceCapture.xcodeproj"
    local app_path="$DERIVED_DATA_DIR/Build/Products/Release-iphonesimulator/Glass Reference Capture.app"

    if [[ ! -d "$project_path" ]]; then
        fail "Capture Xcode project does not exist: $project_path"
        return 1
    fi

    if ! run_with_timeout "$BUILD_TIMEOUT_SECONDS" 'Release capture app build' \
        "$XCODEBUILD" \
        -project "$project_path" \
        -scheme CaptureApp \
        -configuration Release \
        -destination "id=$selected_device_udid" \
        -derivedDataPath "$DERIVED_DATA_DIR" \
        CODE_SIGNING_ALLOWED=NO \
        CODE_SIGNING_REQUIRED=NO \
        build; then
        fail 'Release build failed for the dedicated capture simulator'
        return 1
    fi

    if [[ ! -d "$app_path" ]]; then
        fail "Built capture app was not found at exact expected path: $app_path"
        return 1
    fi
    if ! run_with_timeout "$COMMAND_TIMEOUT_SECONDS" 'capture app installation' \
        "$XCRUN" simctl install "$selected_device_udid" "$app_path"; then
        fail "Unable to install capture app on simulator: $selected_device_udid"
        return 1
    fi
}

wait_for_readiness() {
    local readiness_path="$1"
    local scene="$2"
    local attempt=0

    while ((attempt < 75)); do
        if [[ -f "$readiness_path" ]] &&
            "$JQ" -e --arg scene "$scene" \
                'type == "object" and .scene == $scene' \
                "$readiness_path" >/dev/null 2>&1; then
            return 0
        fi
        attempt=$((attempt + 1))
        if ((attempt < 75)); then
            sleep 0.2
        fi
    done

    fail "Timed out after 15 seconds waiting for valid readiness for scene $scene at $readiness_path"
    return 1
}

validate_readiness() {
    local readiness_path="$1"
    local scene="$2"

    if ! "$JQ" -e \
        --arg scene "$scene" \
        --argjson framebuffer_width "$FRAMEBUFFER_WIDTH" \
        --argjson framebuffer_height "$FRAMEBUFFER_HEIGHT" \
        --argjson viewport_width "$VIEWPORT_WIDTH" \
        --argjson viewport_height "$VIEWPORT_HEIGHT" '
            def allNumbers($values): all($values[]; type == "number");
            .schemaVersion == 1 and
            .scene == $scene and
            .scale == 3 and
            .colorSpace == "sRGB" and
            allNumbers([
                .framebuffer.x,
                .framebuffer.y,
                .framebuffer.width,
                .framebuffer.height,
                .safeAreaInsets.top,
                .safeAreaInsets.leading,
                .safeAreaInsets.bottom,
                .safeAreaInsets.trailing,
                .viewport.x,
                .viewport.y,
                .viewport.width,
                .viewport.height,
                .surfaces.capsule.x,
                .surfaces.capsule.y,
                .surfaces.capsule.width,
                .surfaces.capsule.height,
                .surfaces.card.x,
                .surfaces.card.y,
                .surfaces.card.width,
                .surfaces.card.height,
                .surfaces.panel.x,
                .surfaces.panel.y,
                .surfaces.panel.width,
                .surfaces.panel.height
            ]) and
            .framebuffer == {
                x: 0,
                y: 0,
                width: $framebuffer_width,
                height: $framebuffer_height
            } and
            all([
                .safeAreaInsets.top,
                .safeAreaInsets.leading,
                .safeAreaInsets.bottom,
                .safeAreaInsets.trailing
            ][]; . >= 0) and
            .viewport.width == $viewport_width and
            .viewport.height == $viewport_height and
            .viewport.x == ((.framebuffer.width - .viewport.width) / 2) and
            .viewport.y == ((.framebuffer.height - .viewport.height) / 2) and
            .viewport.x >= .framebuffer.x and
            .viewport.y >= (.framebuffer.y + .safeAreaInsets.top) and
            (.viewport.x + .viewport.width) <=
                (.framebuffer.x + .framebuffer.width) and
            (.viewport.y + .viewport.height) <=
                (.framebuffer.y + .framebuffer.height - .safeAreaInsets.bottom) and
            .surfaces == {
                capsule: { x: 180, y: 270, width: 720, height: 192 },
                card: { x: 120, y: 672, width: 840, height: 528 },
                panel: { x: 60, y: 1380, width: 960, height: 660 }
            }
        ' "$readiness_path" >/dev/null; then
        fail "Readiness payload does not satisfy the strict pixel contract for scene $scene"
        return 1
    fi
}

validate_cropped_png() {
    local png_path="$1"
    local image_format
    local width
    local height
    local color_space

    read_png_property "$png_path" format
    image_format="$png_property_value"
    read_png_property "$png_path" pixelWidth
    width="$png_property_value"
    read_png_property "$png_path" pixelHeight
    height="$png_property_value"
    read_png_property "$png_path" space
    color_space="$png_property_value"

    if [[ "$image_format" != 'png' ]]; then
        fail "Cropped image must be PNG: $png_path (found $image_format)"
        return 1
    fi
    if [[ "$width" != "$VIEWPORT_WIDTH" || "$height" != "$VIEWPORT_HEIGHT" ]]; then
        fail "Cropped image must be ${VIEWPORT_WIDTH}x${VIEWPORT_HEIGHT}: $png_path (found ${width}x${height})"
        return 1
    fi
    if [[ "$color_space" != *RGB* ]]; then
        fail "Cropped image color space must contain RGB: $png_path (found $color_space)"
        return 1
    fi

    measured_png_color_space="$color_space"
}

validate_full_frame_png() {
    local png_path="$1"
    local readiness_path="$2"
    local image_format
    local width
    local height
    local color_space
    local expected_width
    local expected_height

    read_png_property "$png_path" format
    image_format="$png_property_value"
    read_png_property "$png_path" pixelWidth
    width="$png_property_value"
    read_png_property "$png_path" pixelHeight
    height="$png_property_value"
    read_png_property "$png_path" space
    color_space="$png_property_value"
    expected_width="$("$JQ" -er '.framebuffer.width' "$readiness_path")"
    expected_height="$("$JQ" -er '.framebuffer.height' "$readiness_path")"

    if [[ "$image_format" != 'png' ]]; then
        fail "Full-frame screenshot must be PNG: $png_path (found $image_format)"
        return 1
    fi
    if [[ "$width" != "$expected_width" || "$height" != "$expected_height" ]]; then
        fail "Full-frame screenshot must match readiness ${expected_width}x${expected_height}: $png_path (found ${width}x${height})"
        return 1
    fi
    if [[ "$color_space" != *RGB* ]]; then
        fail "Full-frame screenshot color space must contain RGB: $png_path (found $color_space)"
        return 1
    fi
}

capture_scene() {
    local scene="$1"
    local capture_stage="$2"
    local framebuffer_run="$3"
    local readiness_run="$4"
    local data_container
    local documents_directory
    local readiness_path
    local viewport_x
    local viewport_y
    local viewport_width
    local viewport_height
    local full_frame_path="$framebuffer_run/$scene.png"
    local staged_path="$capture_stage/$scene.png"

    if ! run_with_timeout_capture \
        "$COMMAND_TIMEOUT_SECONDS" \
        'capture app container query' \
        "$XCRUN" simctl get_app_container "$selected_device_udid" "$BUNDLE_ID" data; then
        fail "Unable to locate installed app data container for scene $scene"
        return 1
    fi
    data_container="$timed_command_output"
    documents_directory="$data_container/Documents"
    if [[ ! -d "$documents_directory" ]]; then
        fail "Installed app Documents directory does not exist: $documents_directory"
        return 1
    fi
    readiness_path="$documents_directory/capture-ready.json"
    rm -f "$readiness_path"

    if ! run_with_timeout "$COMMAND_TIMEOUT_SECONDS" "capture scene launch ($scene)" \
        "$XCRUN" simctl launch \
        --terminate-running-process \
        "$selected_device_udid" \
        "$BUNDLE_ID" \
        --capture-scene "$scene" >/dev/null; then
        fail "Unable to launch capture scene: $scene"
        return 1
    fi

    wait_for_readiness "$readiness_path" "$scene"
    validate_readiness "$readiness_path" "$scene"
    cp "$readiness_path" "$readiness_run/$scene.json"
    latest_readiness_without_scene="$("$JQ" -cS 'del(.scene)' "$readiness_path")"

    viewport_x="$("$JQ" -er '.viewport.x' "$readiness_path")"
    viewport_y="$("$JQ" -er '.viewport.y' "$readiness_path")"
    viewport_width="$("$JQ" -er '.viewport.width' "$readiness_path")"
    viewport_height="$("$JQ" -er '.viewport.height' "$readiness_path")"

    if ! run_with_timeout "$COMMAND_TIMEOUT_SECONDS" "simulator screenshot ($scene)" \
        "$XCRUN" simctl io "$selected_device_udid" screenshot "$full_frame_path"; then
        fail "Unable to capture full-frame simulator screenshot for scene $scene"
        return 1
    fi
    if [[ ! -f "$full_frame_path" ]]; then
        fail "Simulator screenshot was not written for scene $scene: $full_frame_path"
        return 1
    fi
    validate_full_frame_png "$full_frame_path" "$readiness_path"

    if ! run_with_timeout "$COMMAND_TIMEOUT_SECONDS" "lossless viewport crop ($scene)" \
        "$SIPS" \
        -c "$viewport_height" "$viewport_width" \
        --cropOffset "$viewport_y" "$viewport_x" \
        "$full_frame_path" \
        --out "$staged_path" >/dev/null; then
        fail "Unable to crop viewport without resampling for scene $scene"
        return 1
    fi
    if [[ ! -f "$staged_path" ]]; then
        fail "Cropped screenshot was not written for scene $scene: $staged_path"
        return 1
    fi
    validate_cropped_png "$staged_path"
}

write_manifest() {
    local capture_stage="$1"
    local runtime_description

    runtime_description="$selected_runtime_identifier / $selected_runtime_name / $selected_runtime_version"

    "$JQ" -n \
        --arg xcode "$producer_xcode_version" \
        --arg runtime "$runtime_description" \
        --arg revision "$pinned_revision" \
        --arg osBuild "$selected_runtime_build" \
        --arg colorSpace "$capture_color_space" '{
            schemaVersion: 1,
            platform: "iOS 26",
            osBuild: $osBuild,
            device: "iPhone 17",
            scale: 3,
            colorSpace: $colorSpace,
            material: "Regular",
            tint: "transparent",
            scenes: {
                "uniform-light.png": { appearance: "light", background: "uniform" },
                "uniform-dark.png": { appearance: "dark", background: "uniform" },
                "grid-light.png": { appearance: "light", background: "grid" },
                "grid-dark.png": { appearance: "dark", background: "grid" }
            },
            surfaces: {
                capsule: { x: 180, y: 270, width: 720, height: 192 },
                card: { x: 120, y: 672, width: 840, height: 528 },
                panel: { x: 60, y: 1380, width: 960, height: 660 }
            },
            producer: {
                xcode: $xcode,
                runtime: $runtime,
                revision: $revision
            }
        }' > "$capture_stage/manifest.json"
}

atomic_replace_directory() {
    local requested_source="$1"
    local requested_destination="$2"
    local source_parent
    local source_name
    local source
    local destination_parent
    local destination_name
    local destination
    local backup

    source_parent="$(CDPATH= cd -- "$(dirname "$requested_source")" && pwd -P)"
    source_name="$(basename "$requested_source")"
    source="$source_parent/$source_name"
    destination_parent="$(CDPATH= cd -- "$(dirname "$requested_destination")" && pwd -P)"
    destination_name="$(basename "$requested_destination")"
    destination="$destination_parent/$destination_name"
    backup="$destination_parent/.$destination_name.backup.$$"

    if [[ "$source_parent" != "$destination_parent" ]]; then
        fail "Atomic replacement requires sibling paths on one filesystem: $source -> $destination"
        return 1
    fi
    if [[ ! -d "$source" ]]; then
        fail "Atomic replacement source directory does not exist: $source"
        return 1
    fi
    if [[ -e "$backup" ]]; then
        fail "Atomic replacement backup already exists: $backup"
        return 1
    fi

    # Ignore termination only across the two same-filesystem renames so a previous bundle is never
    # stranded at its backup path by a signal between them.
    trap '' HUP INT TERM
    if [[ -e "$destination" ]]; then
        if ! mv "$destination" "$backup"; then
            install_termination_traps
            fail "Unable to move existing destination to atomic backup: $destination"
            return 1
        fi
    fi

    if ! mv "$source" "$destination"; then
        if [[ -e "$backup" ]] && ! mv "$backup" "$destination"; then
            printf 'capture-references.sh: CRITICAL: unable to restore previous destination; preserve backup at %s\n' \
                "$backup" >&2
        fi
        install_termination_traps
        fail "Unable to install atomic replacement: $destination"
        return 1
    fi
    install_termination_traps

    if [[ -e "$backup" ]]; then
        if ! rm -rf "$backup"; then
            printf 'capture-references.sh: WARNING: backup cleanup failed after atomic install; residual backup may remain: %s\n' \
                "$backup" >&2
        fi
    fi
}

capture_fresh() {
    local capture_stage
    local framebuffer_run
    local readiness_run
    local scene
    local common_readiness=''

    mkdir -p "$CAPTURE_ROOT" "$CAPTURE_ROOT/framebuffers" "$CAPTURE_ROOT/readiness"
    capture_stage="$(mktemp -d "$CAPTURE_ROOT/.current.capture.XXXXXX")"
    capture_staging_path="$capture_stage"
    framebuffer_run="$(mktemp -d "$CAPTURE_ROOT/framebuffers/run.XXXXXX")"
    readiness_run="$(mktemp -d "$CAPTURE_ROOT/readiness/run.XXXXXX")"

    select_simulator
    build_and_install_app

    capture_color_space=''
    for scene in "${CAPTURE_SCENES[@]}"; do
        capture_scene "$scene" "$capture_stage" "$framebuffer_run" "$readiness_run"

        if [[ -z "$common_readiness" ]]; then
            common_readiness="$latest_readiness_without_scene"
        elif [[ "$latest_readiness_without_scene" != "$common_readiness" ]]; then
            fail "Readiness metadata changed between capture scenes (latest: $scene)"
            return 1
        fi

        if [[ -z "$capture_color_space" ]]; then
            capture_color_space="$measured_png_color_space"
        elif [[ "$measured_png_color_space" != "$capture_color_space" ]]; then
            fail "Cropped PNG color space changed between capture scenes (latest: $scene)"
            return 1
        fi
    done

    write_manifest "$capture_stage"
    validate_bundle "$capture_stage" >/dev/null
    verify_pinned_provenance
    atomic_replace_directory "$capture_stage" "$CAPTURE_ROOT/current"
    capture_staging_path=''
    printf 'Validated and atomically staged iOS Glass reference bundle: %s\n' \
        "$(CDPATH= cd -- "$CAPTURE_ROOT/current" && pwd -P)"
}

import_current_bundle() {
    local destination_parent
    local destination_name
    local scene

    destination_parent="$(dirname "$IMPORT_DESTINATION")"
    destination_name="$(basename "$IMPORT_DESTINATION")"
    mkdir -p "$destination_parent"
    destination_parent="$(CDPATH= cd -- "$destination_parent" && pwd -P)"
    import_staging_path="$(mktemp -d "$destination_parent/.$destination_name.import.XXXXXX")"

    cp "$CAPTURE_ROOT/current/manifest.json" "$import_staging_path/manifest.json"
    for scene in "${SCENE_NAMES[@]}"; do
        cp "$CAPTURE_ROOT/current/$scene" "$import_staging_path/$scene"
    done

    validate_bundle "$import_staging_path" >/dev/null
    verify_pinned_provenance
    atomic_replace_directory "$import_staging_path" "$destination_parent/$destination_name"
    import_staging_path=''
    printf 'Imported fresh iOS Glass reference bundle atomically: %s\n' \
        "$destination_parent/$destination_name"
}

capture_mode() {
    local should_import="$1"
    local required

    for required in \
        "$XCRUN" \
        "$XCODEBUILD" \
        "$JQ" \
        "$SIPS" \
        "$GIT" \
        cp \
        mkdir \
        mktemp \
        mv \
        rm \
        rmdir \
        sleep; do
        require_command "$required"
    done

    acquire_capture_lock
    verify_capture_provenance
    capture_fresh
    if [[ "$should_import" -eq 1 ]]; then
        import_current_bundle
    fi
}

case "$#" in
    0)
        capture_mode 0
        ;;
    1)
        if [[ "$1" == '--import' ]]; then
            capture_mode 1
        else
            usage
            fail 'Unsupported arguments'
        fi
        ;;
    2)
        if [[ "$1" == '--validate-only' ]]; then
            validate_bundle "$2"
        else
            usage
            fail 'Unsupported arguments'
        fi
        ;;
    *)
        usage
        fail 'Unsupported arguments'
        ;;
esac
