#!/bin/sh
# Turn a release tag (vMAJOR.MINOR.PATCH[-suffix]) into the version and the pre-release flag.
# Prints "version=..." and "prerelease=..." (the format of $GITHUB_OUTPUT). Fails on an invalid tag.
tag=$1
# Numbers without leading zeros; an optional suffix of dot-separated alphanumerics (for example rc.1).
number='\(0\|[1-9][0-9]*\)'
suffix='[0-9A-Za-z]\+\(\.[0-9A-Za-z]\+\)*'
if ! printf '%s\n' "$tag" | grep -q "^v$number\.$number\.$number\(-$suffix\)\{0,1\}$"; then
	echo "invalid release tag '$tag': expected vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-suffix" >&2
	exit 1
fi
version=${tag#v}
case $version in
*-*) prerelease=true ;;
*) prerelease=false ;;
esac
echo "version=$version"
echo "prerelease=$prerelease"
