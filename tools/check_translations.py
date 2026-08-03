#!/usr/bin/env python3
"""Verify every English UI string has a translation in all 20 locale files.

Why this exists
---------------
`AppLocalization` resolves strings as `translations[lang][key] ?: english[key] ?: key`.
That English fallback means a key missing from a locale never crashes and never logs —
the screen just renders English in the middle of Russian, Japanese, Arabic. It is
invisible unless someone happens to run the app in that language and read the screen.

On 2026-07-30 an audit found 19 keys that were English-only in all 20 locales — 380
missing entries — including the *entire* overlay permission disclosure and every
withdrawal string. A Play reviewer testing in a non-English locale would have been shown
a prominent disclosure they could not read.

Usage
-----
    python3 tools/check_translations.py            # report, exit 1 if incomplete
    python3 tools/check_translations.py --quiet    # only print problems

Run it before every release. Adding a key to Strings.kt alone is not enough, and nothing
else in the build will tell you.

Known limitation
----------------
This checks that a key *exists*, not that its translation is current. Rewording an
English string leaves all 20 translations describing the old wording — still valid
sentences, so the diff stays clean. When you rewrite English copy, re-translate it
deliberately; this script cannot detect that case.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys
from collections import defaultdict

LOCALIZATION_DIR = (
    pathlib.Path(__file__).resolve().parent.parent
    / "app/src/main/java/com/spikeai/app/localization"
)

# Matches:    "some_key" to "..."
KEY_RE = re.compile(r'^\s*"([a-zA-Z0-9_]+)"\s+to\s+', re.M)


def keys_in(path: pathlib.Path) -> set[str]:
    return set(KEY_RE.findall(path.read_text(encoding="utf-8")))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="only print locales that have problems",
    )
    args = parser.parse_args()

    english_file = LOCALIZATION_DIR / "Strings.kt"
    if not english_file.exists():
        print(f"error: {english_file} not found", file=sys.stderr)
        return 2

    english = keys_in(english_file)
    locale_files = sorted(LOCALIZATION_DIR.glob("Strings_*.kt"))
    if not locale_files:
        print(f"error: no locale files in {LOCALIZATION_DIR}", file=sys.stderr)
        return 2

    if not args.quiet:
        print(f"English keys: {len(english)}")
        print(f"Locales:      {len(locale_files)}\n")

    missing_by_key: dict[str, list[str]] = defaultdict(list)
    obsolete_by_key: dict[str, list[str]] = defaultdict(list)
    total_missing = 0

    for path in locale_files:
        code = path.stem.removeprefix("Strings_")
        present = keys_in(path)
        missing = english - present
        # Keys a locale defines that English no longer has: dead weight, and a hint
        # that a key was renamed in English without the locales following.
        obsolete = present - english

        total_missing += len(missing)
        for key in missing:
            missing_by_key[key].append(code)
        for key in obsolete:
            obsolete_by_key[key].append(code)

        if missing or obsolete:
            bits = []
            if missing:
                bits.append(f"{len(missing)} missing")
            if obsolete:
                bits.append(f"{len(obsolete)} obsolete")
            print(f"  {code}: {', '.join(bits)}")
        elif not args.quiet:
            print(f"  {code}: complete")

    if missing_by_key:
        print("\nMissing keys (add these to the listed locale files):")
        for key, locales in sorted(
            missing_by_key.items(), key=lambda kv: (-len(kv[1]), kv[0])
        ):
            print(f"  {key}  ->  {' '.join(sorted(locales))}")

    if obsolete_by_key:
        print("\nObsolete keys (present in a locale, gone from English):")
        for key, locales in sorted(obsolete_by_key.items()):
            print(f"  {key}  ->  {' '.join(sorted(locales))}")

    if total_missing:
        print(f"\nFAIL: {total_missing} missing entries.", file=sys.stderr)
        return 1

    if not args.quiet:
        print("\nOK: every English key is present in every locale.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
