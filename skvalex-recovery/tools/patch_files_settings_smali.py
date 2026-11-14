#!/usr/bin/env python3
"""Apply narrowly scoped, non-capture Files-settings string fallbacks to apktool output."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

CALL = "Lo/w80;->c(J)Ljava/lang/String;"
CASES = (
    {
        "descriptor": "Lorg/skvalex/cr/Settings$FilesSettingsFragment;",
        "method": "<clinit>()V",
        "key": "7b5801d2b835",
        "replacement": None,
        "kind": "discarded-return",
    },
    {
        "descriptor": "Lo/pg2;",
        "method": "a(Landroidx/preference/Preference;)Z",
        "key": "7b2c01d2b835",
        "replacement": "skvalex.files.folder-picker",
        "kind": "string-result",
    },
    {
        "descriptor": "Lo/pg2;",
        "method": "a(Landroidx/preference/Preference;)Z",
        "key": "7b4201d2b835",
        "replacement": "skvalex.files.folder-picker.dialog",
        "kind": "string-result",
    },
    {
        "descriptor": "Lo/rg2;",
        "method": "a(Landroidx/preference/Preference;)Z",
        "key": "7b0e01d2b835",
        "replacement": "skvalex.files.sim-selection.dialog",
        "kind": "string-result",
    },
}


def find_smali(root: Path, descriptor: str) -> Path:
    relative = descriptor[1:-1] + ".smali"
    matches = list(root.glob("smali*/" + relative))
    if len(matches) != 1:
        raise RuntimeError(
            f"Expected exactly one smali file for {descriptor}; found {len(matches)}"
        )
    return matches[0]


def method_span(text: str, signature: str) -> tuple[int, int]:
    lines = text.splitlines(keepends=True)
    starts = [i for i, line in enumerate(lines) if line.startswith(".method ") and signature in line]
    if len(starts) != 1:
        raise RuntimeError(f"Expected one method containing {signature}; found {len(starts)}")
    start = starts[0]
    ends = [i for i in range(start + 1, len(lines)) if lines[i].strip() == ".end method"]
    if not ends:
        raise RuntimeError(f"Missing .end method for {signature}")
    return start, ends[0] + 1


def patch_case(root: Path, case: dict) -> Path:
    path = find_smali(root, case["descriptor"])
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    start, end = method_span(text, case["method"])
    block = "".join(lines[start:end])
    key = case["key"]

    if case["kind"] == "discarded-return":
        pattern = re.compile(
            rf"(?m)^(?P<indent>\\s*)const-wide\\s+(?P<reg>v\\d+),\\s*-0x{key}L\\s*\\n"
            rf"\\s*invoke-static\\s*\\{{\\s*(?P=reg),\\s*v\\d+\\s*\\}},\\s*{re.escape(CALL)}\\s*\\n"
        )
        replacement = ""
    else:
        pattern = re.compile(
            rf"(?m)^(?P<indent>\\s*)const-wide\\s+(?P<reg>v\\d+),\\s*-0x{key}L\\s*\\n"
            rf"\\s*invoke-static\\s*\\{{\\s*(?P=reg),\\s*v\\d+\\s*\\}},\\s*{re.escape(CALL)}\\s*\\n"
            rf"\\s*move-result-object\\s+(?P=reg)\\s*\\n"
        )
        value = case["replacement"].replace("\\", "\\\\").replace('"', '\\"')
        replacement = lambda m: f'{m.group("indent")}const-string {m.group("reg")}, "{value}"\\n'

    updated, count = pattern.subn(replacement, block)
    if count != 1:
        raise RuntimeError(
            f"{case['descriptor']} {case['method']} key 0x{key}: expected one exact sequence, found {count}"
        )
    lines[start:end] = [updated]
    path.write_text("".join(lines), encoding="utf-8")
    return path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("decoded_apk", type=Path, help="apktool decoded project directory")
    args = parser.parse_args()
    root = args.decoded_apk.resolve()
    if not root.is_dir():
        raise SystemExit(f"Not a decoded project directory: {root}")

    touched = []
    for case in CASES:
        touched.append(patch_case(root, case))

    # Fail closed if any target decoder call remains in the affected methods.
    for descriptor, signature in {
        ("Lorg/skvalex/cr/Settings$FilesSettingsFragment;", "<clinit>()V"),
        ("Lo/pg2;", "a(Landroidx/preference/Preference;)Z"),
        ("Lo/rg2;", "a(Landroidx/preference/Preference;)Z"),
    }:
        path = find_smali(root, descriptor)
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines(keepends=True)
        start, end = method_span(text, signature)
        if CALL in "".join(lines[start:end]):
            raise RuntimeError(f"Unresolved decoder call remains in {descriptor} {signature}")

    for path in sorted(set(touched)):
        print(f"patched: {path.relative_to(root)}")
    print("Applied exactly four non-capture Files-settings compatibility fallbacks.")


if __name__ == "__main__":
    main()
