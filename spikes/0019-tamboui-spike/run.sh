#!/bin/sh
# THROWAWAY spike. Needs Java 25. Usage: ./run.sh [--dump]   (KEYS=legacy ./run.sh turns the Kitty keyboard protocol off)
cd "$(dirname "$0")" || exit 1
[ -d target/lib ] || ../../mvnw -q -f pom.xml package || exit 1
exec java --enable-native-access=ALL-UNNAMED -cp "target/classes:target/lib/*" Spike "$@"
