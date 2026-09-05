#!/usr/bin/env python3
"""Verify a generated profile against its six Haze AARs and consumer DEX files."""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zipfile
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree


EXPECTED_AARS = {
    "haze",
    "haze-blur",
    "haze-glass",
    "haze-utils",
    "haze-materials",
    "haze-glass-material3",
}
PROFILE_RE = re.compile(r"(?P<flags>[HSP]*)(?P<owner>L[^;]+;)(?:->(?P<member>[^\s]+))?$")


class VerificationError(Exception):
    pass


@dataclass(frozen=True)
class ProfileRule:
    line: str
    flags: str
    owner: str
    member: str | None


@dataclass
class ClassInfo:
    methods: set[str]
    access_flags: int


@dataclass
class AarInfo:
    name: str
    path: Path
    classes: dict[str, ClassInfo]
    namespaces: set[str]
    assets: list[str]


def decode_mutf8(data: bytes) -> str:
    """Decode the modified UTF-8 used by JVM class files and DEX strings."""
    chars = []
    offset = 0
    while offset < len(data):
        first = data[offset]
        if first < 0x80:
            chars.append(chr(first))
            offset += 1
        elif first & 0xE0 == 0xC0:
            if offset + 1 >= len(data):
                raise VerificationError("truncated modified UTF-8 sequence")
            second = data[offset + 1]
            if second & 0xC0 != 0x80:
                raise VerificationError("invalid modified UTF-8 continuation")
            chars.append(chr(((first & 0x1F) << 6) | (second & 0x3F)))
            offset += 2
        elif first & 0xF0 == 0xE0:
            if offset + 2 >= len(data):
                raise VerificationError("truncated modified UTF-8 sequence")
            second, third = data[offset + 1], data[offset + 2]
            if second & 0xC0 != 0x80 or third & 0xC0 != 0x80:
                raise VerificationError("invalid modified UTF-8 continuation")
            chars.append(chr(((first & 0x0F) << 12) | ((second & 0x3F) << 6) | (third & 0x3F)))
            offset += 3
        else:
            raise VerificationError("unsupported modified UTF-8 sequence")
    return "".join(chars)


def read_u(data: bytes, offset: int, fmt: str):
    size = struct.calcsize("<" + fmt)
    if offset < 0 or offset + size > len(data):
        raise VerificationError("truncated binary input")
    values = struct.unpack_from("<" + fmt, data, offset)
    return (values[0] if len(values) == 1 else values), offset + size


def read_be(data: bytes, offset: int, fmt: str):
    size = struct.calcsize(">" + fmt)
    if offset < 0 or offset + size > len(data):
        raise VerificationError("truncated class file")
    values = struct.unpack_from(">" + fmt, data, offset)
    return (values[0] if len(values) == 1 else values), offset + size


def class_file_info(data: bytes) -> tuple[str, ClassInfo]:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise VerificationError("invalid class file magic")
    offset = 8
    cp_count, offset = read_be(data, offset, "H")
    cp = [None] * cp_count
    index = 1
    while index < cp_count:
        tag, offset = read_be(data, offset, "B")
        if tag == 1:
            length, offset = read_be(data, offset, "H")
            end = offset + length
            if end > len(data):
                raise VerificationError("truncated class UTF-8 constant")
            cp[index] = decode_mutf8(data[offset:end])
            offset = end
        elif tag in (7, 8, 16, 19, 20):
            cp[index], offset = read_be(data, offset, "H")
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
            if offset > len(data):
                raise VerificationError("truncated class constant")
        elif tag in (5, 6):
            offset += 8
            if offset > len(data):
                raise VerificationError("truncated wide class constant")
            index += 1
        elif tag == 15:
            offset += 3
            if offset > len(data):
                raise VerificationError("truncated method-handle constant")
        else:
            raise VerificationError(f"unknown class constant tag {tag}")
        index += 1

    class_access_flags, offset = read_be(data, offset, "H")
    this_class, offset = read_be(data, offset, "H")
    _, offset = read_be(data, offset, "H")
    name_index = cp[this_class]
    if not isinstance(name_index, int) or not isinstance(cp[name_index], str):
        raise VerificationError("invalid class name constant")
    owner = "L" + cp[name_index] + ";"

    interfaces, offset = read_be(data, offset, "H")
    offset += interfaces * 2

    def skip_attributes(position: int) -> int:
        count, position = read_be(data, position, "H")
        for _ in range(count):
            _, position = read_be(data, position, "H")
            length, position = read_be(data, position, "I")
            position += length
            if position > len(data):
                raise VerificationError("truncated class attribute")
        return position

    fields, offset = read_be(data, offset, "H")
    for _ in range(fields):
        offset += 6  # access_flags, name_index, descriptor_index
        if offset > len(data):
            raise VerificationError("truncated field")
        offset = skip_attributes(offset)

    methods, offset = read_be(data, offset, "H")
    defined_methods: set[str] = set()
    for _ in range(methods):
        (_, name_index, descriptor_index, attribute_count), offset = read_be(data, offset, "HHHH")
        if not isinstance(cp[name_index], str) or not isinstance(cp[descriptor_index], str):
            raise VerificationError("invalid method constant")
        defined_methods.add(cp[name_index] + cp[descriptor_index])
        for _ in range(attribute_count):
            _, offset = read_be(data, offset, "H")
            length, offset = read_be(data, offset, "I")
            offset += length
            if offset > len(data):
                raise VerificationError("truncated method attribute")
    return owner, ClassInfo(defined_methods, class_access_flags)


def parse_manifest(data: bytes) -> set[str]:
    try:
        root = ElementTree.fromstring(data)
    except ElementTree.ParseError as error:
        raise VerificationError("AAR manifest is not text XML") from error
    package = root.attrib.get("package")
    return {package} if package else set()


def parse_aar(path: Path) -> AarInfo:
    name = path.stem
    if name not in EXPECTED_AARS:
        raise VerificationError(f"unexpected AAR name: {path}")
    if not path.is_file():
        raise VerificationError(f"missing AAR: {path}")
    classes: dict[str, ClassInfo] = {}
    with zipfile.ZipFile(path) as aar:
        names = aar.namelist()
        if "classes.jar" not in names:
            raise VerificationError(f"{path} has no classes.jar")
        manifests = [name for name in names if name == "AndroidManifest.xml"]
        namespaces = set()
        for manifest in manifests:
            namespaces.update(parse_manifest(aar.read(manifest)))
        with zipfile.ZipFile(aar.open("classes.jar")) as jar:
            for class_name in jar.namelist():
                if not class_name.endswith(".class") or class_name == "module-info.class":
                    continue
                owner, info = class_file_info(jar.read(class_name))
                if owner in classes:
                    raise VerificationError(f"duplicate class in {path}: {owner}")
                classes[owner] = info
        assets = [name for name in names if name.rsplit("/", 1)[-1] == "baseline-prof.txt"]
    return AarInfo(name, path, classes, namespaces, assets)


def uleb(data: bytes, offset: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte, offset = read_u(data, offset, "B")
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, offset
        shift += 7
        if shift > 35:
            raise VerificationError("invalid ULEB128 value")


def dex_definitions(data: bytes) -> dict[str, set[str]]:
    if len(data) < 112 or data[:4] != b"dex\n" or data[7:8] != b"\0":
        raise VerificationError("invalid DEX file")

    def uint(offset: int) -> int:
        return read_u(data, offset, "I")[0]

    def ushort(offset: int) -> int:
        return read_u(data, offset, "H")[0]

    string_count, string_offset = uint(56), uint(60)
    strings = []
    for index in range(string_count):
        item_offset = uint(string_offset + index * 4)
        _, item_offset = uleb(data, item_offset)
        end = data.find(b"\0", item_offset)
        if end < 0:
            raise VerificationError("unterminated DEX string")
        strings.append(decode_mutf8(data[item_offset:end]))

    type_count, type_offset = uint(64), uint(68)
    types = [strings[uint(type_offset + index * 4)] for index in range(type_count)]
    proto_count, proto_offset = uint(72), uint(76)
    protos = []
    for index in range(proto_count):
        item = proto_offset + index * 12
        return_type, parameters = uint(item + 4), uint(item + 8)
        parameter_count = uint(parameters) if parameters else 0
        parameter_types = "".join(types[ushort(parameters + 4 + n * 2)] for n in range(parameter_count))
        protos.append("(" + parameter_types + ")" + types[return_type])

    method_count, method_offset = uint(88), uint(92)
    methods = []
    for index in range(method_count):
        item = method_offset + index * 8
        owner, name, proto = ushort(item), uint(item + 4), ushort(item + 2)
        methods.append((types[owner], strings[name] + protos[proto]))

    result: dict[str, set[str]] = {}
    class_count, class_offset = uint(96), uint(100)
    for index in range(class_count):
        item = class_offset + index * 32
        owner = types[uint(item)]
        members = result.setdefault(owner, set())
        class_data_offset = uint(item + 24)
        if not class_data_offset:
            continue
        counts = []
        class_data_offset_start = class_data_offset
        for _ in range(4):
            count, class_data_offset = uleb(data, class_data_offset)
            counts.append(count)
        for _ in range(counts[0] + counts[1]):
            _, class_data_offset = uleb(data, class_data_offset)
            _, class_data_offset = uleb(data, class_data_offset)
        for count in counts[2:]:
            method_index = 0
            for _ in range(count):
                delta, class_data_offset = uleb(data, class_data_offset)
                method_index += delta
                _, class_data_offset = uleb(data, class_data_offset)
                _, class_data_offset = uleb(data, class_data_offset)
                method_owner, method = methods[method_index]
                if method_owner != owner:
                    raise VerificationError("DEX class-data owner mismatch")
                members.add(method)
        if class_data_offset <= class_data_offset_start:
            raise VerificationError("DEX class-data did not advance")
    return result


def parse_profile(path: Path) -> tuple[bytes, list[ProfileRule]]:
    if not path.is_file():
        raise VerificationError(f"missing profile: {path}")
    raw = path.read_bytes()
    rules = []
    for line_number, raw_line in enumerate(raw.splitlines(), 1):
        line = raw_line.decode("utf-8")
        if not line or line.startswith("#"):
            continue
        match = PROFILE_RE.fullmatch(line)
        if not match:
            raise VerificationError(f"malformed profile line {line_number}: {line}")
        rules.append(ProfileRule(line, match["flags"], match["owner"], match["member"]))
    if not rules:
        raise VerificationError("profile contains no rules")
    return raw, rules


def classify(rule: ProfileRule, aars: dict[str, AarInfo], consumer: dict[str, set[str]]):
    owner = rule.owner
    if not owner.startswith("Ldev/chrisbanes/haze/") or "/sample/" in owner:
        raise VerificationError(f"foreign or sample owner: {rule.line}")

    consumer_members = consumer.get(owner)
    if consumer_members is None or (rule.member and rule.member not in consumer_members):
        raise VerificationError(f"missing consumer definition: {rule.line}")

    aar_matches = [name for name, aar in aars.items() if owner in aar.classes]
    if len(aar_matches) == 1:
        class_info = aars[aar_matches[0]].classes[owner]
        if not rule.member or rule.member in class_info.methods:
            return "ordinary", aar_matches[0]
    elif len(aar_matches) > 1:
        raise VerificationError(f"ordinary owner is duplicated in AARs: {rule.line}")

    if "$$ExternalSynthetic" in owner:
        return "D8ExternalSynthetic", None
    if rule.member and "$r8$lambda$" in rule.member:
        return "D8LambdaBridge", None
    if owner.endswith("$-CC;"):
        counterpart = owner[:-5] + ";"
        matches = [aar for aar in aars.values() if counterpart in aar.classes]
        if len(matches) != 1 or not matches[0].classes[counterpart].access_flags & 0x0200:
            raise VerificationError(f"$-CC owner has no AAR interface counterpart: {rule.line}")
        return "D8InterfaceCompanion", None
    if owner.endswith("/R$drawable;"):
        owner_namespace = owner[1:-len("/R$drawable;")].replace("/", ".")
        if not any(owner_namespace in aar.namespaces for aar in aars.values()):
            raise VerificationError(f"resource owner has no AAR namespace: {rule.line}")
        if rule.member:
            raise VerificationError(f"resource rule must be a class rule: {rule.line}")
        return "AndroidResourceClass", None

    raise VerificationError(f"ordinary owner or method missing in AARs: {rule.line}")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True, type=Path)
    parser.add_argument("--aar", action="append", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    return parser.parse_args()


def verify(args) -> dict:
    if {path.stem for path in args.aar} != EXPECTED_AARS or len(args.aar) != len(EXPECTED_AARS):
        raise VerificationError("exactly the six expected Haze AARs are required")
    profile_bytes, rules = parse_profile(args.profile)
    aars_list = [parse_aar(path) for path in args.aar]
    aars = {aar.name: aar for aar in aars_list}
    if not args.apk.is_file():
        raise VerificationError(f"missing consumer APK: {args.apk}")
    consumer: dict[str, set[str]] = {}
    with zipfile.ZipFile(args.apk) as apk:
        dex_names = sorted(name for name in apk.namelist() if re.fullmatch(r"classes\d*\.dex", name))
        if not dex_names:
            raise VerificationError("consumer APK contains no classes*.dex")
        for name in dex_names:
            for owner, methods in dex_definitions(apk.read(name)).items():
                if owner in consumer:
                    consumer[owner].update(methods)
                else:
                    consumer[owner] = methods

    ordinary = Counter()
    generated = Counter()
    for rule in rules:
        category, aar_name = classify(rule, aars, consumer)
        if category == "ordinary":
            ordinary[aar_name] += 1
        else:
            generated[category] += 1

    assets = {name: aar.assets for name, aar in aars.items()}
    root = aars["haze"]
    if root.assets != ["baseline-prof.txt"]:
        raise VerificationError(f"root AAR profile assets are not exactly baseline-prof.txt: {root.assets}")
    if zipfile.ZipFile(root.path).read("baseline-prof.txt") != profile_bytes:
        raise VerificationError("root AAR baseline-prof.txt is not byte-identical to the checked-in profile")
    for name, aar in aars.items():
        if name != "haze" and aar.assets:
            raise VerificationError(f"dependent AAR contains a profile asset: {name}: {aar.assets}")

    result = {
        "profile": {"rules": len(rules), "classRules": sum(not rule.member for rule in rules),
                     "methodRules": sum(bool(rule.member) for rule in rules)},
        "ordinaryByAar": dict(sorted(ordinary.items())),
        "generatedRules": dict(sorted(generated.items())),
        "consumerDex": {"files": len(dex_names), "definedClasses": len(consumer)},
        "aarProfileAssets": assets,
        "missing": [],
    }
    return result


def main() -> int:
    try:
        result = verify(parse_args())
    except (OSError, ValueError, VerificationError, zipfile.BadZipFile, ElementTree.ParseError) as error:
        print(json.dumps({"status": "failed", "error": str(error)}), file=sys.stderr)
        return 1
    print(json.dumps({"status": "ok", **result}, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
