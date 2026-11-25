#!/usr/bin/env bash
# Regenerates everything derived from the live BankParserFactory registry:
#
#   SupportedBanksDocTest -> docs/supported-banks.json
#                            README supported-banks block + summary bullet
#                            the coverage claim in the Play Store long description
#
# Run this after adding/removing/renaming a bank parser, or after changing a
# parser's extraction behaviour. CI runs this generator in assert mode
# (parser-core:jvmTest), so anything stale fails the build with a pointer here.
set -euo pipefail
cd "$(dirname "$0")/.."

# --rerun-tasks is required: without it Gradle treats the tests as UP-TO-DATE on
# repeat runs and skips them, so the generated files would not be rewritten. Only
# this on-demand regen uses it; the CI staleness guard runs the tests normally.
UPDATE_SUPPORTED_BANKS=true ./gradlew :parser-core:jvmTest \
  --tests "com.pennywiseai.parser.core.SupportedBanksDocTest" \
  --rerun-tasks

echo "Updated the supported-banks catalogue, the README block, and the Play listing"
echo "coverage claim."
