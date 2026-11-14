#!/usr/bin/env python3
"""Verify the pristine SKVALEX 3.7.3 APK baseline without modifying it.

This utility checks only file integrity and DEX container headers. It does not
install, execute, patch, resign, or inspect audio/recording behavior.
"""
from __future__ import annotations

import argparse
import hashlib
import sys
import zipfile
from pathlib import Path

EXPECTED_SHA256 = "db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684"
REQUIRED_ENTRIES = {
    "AndroidManifest.xml",
    "classes.dex",
    "classes2.dex",
    "resources.arsc",
}
DEX_MAGIC_PREFIX = b"dex\n"
DEX_HEADER_SIZE = 0x70
DEX_ENDIAN_CONSTANT = 0x12345678


class VerificationError(Exception):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def check_dex_header(archive: zipfile.ZipFile, entry: str) -> None:
    data = archive.read(entry)
    if len(data) < DEX_HEADER_SIZE or not data.startswith(DEX_MAGIC_PREFIX):
        raise VerificationError(f"{entry}: invalid or truncated DEX header")
    declared_size = int.from_bytes(data[32:36], "little")
    header_size = int.from_bytes(data[36:40], "little")
    endian_tag = int.from_bytes(data[40:44], "little")
    if declared_size != len(data):
        raise VerificationError(
            f"{entry}: header file_size={declared_size}, actual={len(data)}"
        )
    if header_size != DEX_HEADER_SIZE:
        raise VerificationError(f"{entry}: unexpected header_size={header_size}")
    if endian_tag != DEX_ENDIAN_CONSTANT:
        raise VerificationError(f"{entry}: unexpected endian tag=0x{endian_tag:08x}")


def verify(path: Path) -> None:
    if not path.is_file():
        raise VerificationError(f"APK not found: {path}")

    actual_hash = sha256_file(path)
    if actual_hash != EXPECTED_SHA256:
        raise VerificationError(
            "SHA-256 does not match the pristine reference. "
            f"Expected {EXPECTED_SHA256}; got {actual_hash}."
        )

    try:
        with zipfile.ZipFile(path, "r") as archive:
            names = set(archive.namelist())
            missing = sorted(REQUIRED_ENTRIES - names)
            if missing:
                raise VerificationError("Missing APK entries: " + ", ".join(missing))
            corrupt_entry = archive.testzip()
            if corrupt_entry is not None:
                raise VerificationError(f"ZIP CRC failure in: {corrupt_entry}")
            check_dex_header(archive, "classes.dex")
            check_dex_header(archive, "classes2.dex")
    except zipfile.BadZipFile as exc:
        raise VerificationError(f"Invalid APK/ZIP container: {exc}") from exc

    print("PASS: pristine APK SHA-256 matches the recorded baseline")
    print("PASS: APK ZIP entries and CRC checks are valid")
    print("PASS: both DEX headers and declared file sizes are consistent")
    print(f"SHA-256: {actual_hash}")
    print("No APK bytes were changed.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="path to the original base(3).apk")
    args = parser.parse_args()
    try:
        verify(args.apk)
    except (OSError, VerificationError, zipfile.LargeZipFile) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
