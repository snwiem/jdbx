#!/bin/sh
# THROWAWAY spike. Needs Java 25 and Maven. Usage: ./run.sh   (rebind newline: KEY_NEWLINE=$'\033\r' ./run.sh)
cd "$(dirname "$0")" || exit 1
[ -d target/lib ] || mvn -q package || exit 1
exec java --enable-native-access=ALL-UNNAMED ${KEY_NEWLINE:+-Dkey.newline="$KEY_NEWLINE"} -cp "target/classes:target/lib/*" Spike
