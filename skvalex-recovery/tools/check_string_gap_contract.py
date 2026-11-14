#!/usr/bin/env python3
"""Audit the four known SKVALEX 3.7.3 string-decoder boundary call sites.

This is recovery tooling only. It does not patch DEX/APK files or synthesize
replacement strings. UTF-16 code units are used to match Java String indexing.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

ResultUse = Literal["discarded", "consumed"]


@dataclass(frozen=True)
class CallSite:
    name: str
    offset: int
    requested: int
    result_use: ResultUse


@dataclass(frozen=True)
class Finding:
    name: str
    available: int
    requested: int
    out_of_bounds: bool
    result_use: ResultUse


CHUNK_UTF16_UNITS = 7025
CALL_SITES = (
    CallSite("FilesSettingsFragment.<clinit>", 7003, 110, "discarded"),
    CallSite("Lo/pg2.a(Preference)#1", 6981, 110, "consumed"),
    CallSite("Lo/pg2.a(Preference)#2", 6959, 110, "consumed"),
    CallSite("Lo/rg2.a(Preference)", 6929, 110, "consumed"),
)


def utf16_length(value: str) -> int:
    """Return Java String.length()-equivalent UTF-16 code-unit count."""
    return len(value.encode("utf-16-le", errors="surrogatepass")) // 2


def inspect_call_sites(
    chunk_utf16_units: int = CHUNK_UTF16_UNITS,
    call_sites: tuple[CallSite, ...] = CALL_SITES,
) -> tuple[Finding, ...]:
    if chunk_utf16_units < 0:
        raise ValueError("chunk length cannot be negative")

    findings: list[Finding] = []
    for call in call_sites:
        if call.offset < 0 or call.requested < 0:
            raise ValueError(f"negative offset/request in {call.name}")
        available = max(0, chunk_utf16_units - call.offset)
        findings.append(
            Finding(
                name=call.name,
                available=available,
                requested=call.requested,
                out_of_bounds=call.offset + call.requested > chunk_utf16_units,
                result_use=call.result_use,
            )
        )
    return tuple(findings)


def validate_known_373_contract() -> tuple[Finding, ...]:
    """Fail closed if the recorded 3.7.3 evidence changes unexpectedly."""
    findings = inspect_call_sites()
    expected = (22, 44, 66, 96)
    actual = tuple(item.available for item in findings)
    if actual != expected:
        raise AssertionError(f"unexpected UTF-16 tail lengths: {actual}")
    if not all(item.out_of_bounds for item in findings):
        raise AssertionError("expected all four 110-unit reads to exceed the chunk")
    if tuple(item.result_use for item in findings) != (
        "discarded", "consumed", "consumed", "consumed"
    ):
        raise AssertionError("decoder return-use contract changed")
    return findings


if __name__ == "__main__":
    for item in validate_known_373_contract():
        print(
            f"{item.name}: available={item.available}, "
            f"requested={item.requested}, out_of_bounds={item.out_of_bounds}, "
            f"result={item.result_use}"
        )
    print("PASS: recorded 3.7.3 string-gap contract is internally consistent")
