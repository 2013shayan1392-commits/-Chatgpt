import unittest

from check_string_gap_contract import (
    CallSite,
    CHUNK_UTF16_UNITS,
    inspect_call_sites,
    utf16_length,
    validate_known_373_contract,
)


class StringGapContractTests(unittest.TestCase):
    def test_java_utf16_length_not_python_codepoint_length(self):
        self.assertEqual(utf16_length("A😀B"), 4)

    def test_known_373_tail_lengths_and_usage(self):
        findings = validate_known_373_contract()
        self.assertEqual([item.available for item in findings], [22, 44, 66, 96])
        self.assertEqual(
            [item.result_use for item in findings],
            ["discarded", "consumed", "consumed", "consumed"],
        )
        self.assertTrue(all(item.out_of_bounds for item in findings))

    def test_in_bounds_call_is_not_flagged(self):
        call = CallSite("fixture", 5, 3, "consumed")
        finding = inspect_call_sites(10, (call,))[0]
        self.assertEqual(finding.available, 5)
        self.assertFalse(finding.out_of_bounds)

    def test_invalid_negative_offset_is_rejected(self):
        with self.assertRaises(ValueError):
            inspect_call_sites(10, (CallSite("bad", -1, 3, "consumed"),))

    def test_chunk_length_matches_recorded_dex_utf16_size(self):
        self.assertEqual(CHUNK_UTF16_UNITS, 7025)


if __name__ == "__main__":
    unittest.main()
