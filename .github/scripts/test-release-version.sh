#!/bin/sh
# Tests release-version.sh at its external behavior.
# Run: sh .github/scripts/test-release-version.sh
here=$(cd "$(dirname "$0")" && pwd)
script=$here/release-version.sh
pass=0
fail=0

# check NAME TAG EXPECTED_OUTPUT EXPECTED_EXIT
check() {
	out=$(sh "$script" "$2" 2>/dev/null)
	code=$?
	if [ "$out" = "$3" ] && [ "$code" = "$4" ]; then
		pass=$((pass + 1))
	else
		fail=$((fail + 1))
		printf 'FAIL: %s\n  tag=%s\n  exit=%s (want %s)\n  output=%s\n  want=%s\n' "$1" "$2" "$code" "$4" "$out" "$3"
	fi
}

nl='
'
check "release tag gives version, no pre-release" "v0.1.0" "version=0.1.0${nl}prerelease=false" 0
check "tag with suffix is a pre-release" "v0.1.0-rc.1" "version=0.1.0-rc.1${nl}prerelease=true" 0
for bad in "0.1.0" "v1.2" "v1.2.3.4" "v1.2.x" "v01.2.3" "v1.2.3-" "v1.2.3 " "latest" ""; do
	check "invalid tag '$bad' fails with no output" "$bad" "" 1
done

echo "$pass passed, $fail failed"
[ "$fail" = 0 ]
