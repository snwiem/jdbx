#!/bin/sh
# Tests the jdbx wrapper at its external behavior, using stub 'java' executables.
# Run: sh bin/test-jdbx.sh
here=$(cd "$(dirname "$0")" && pwd)
wrapper=$here/jdbx
tmp=$(mktemp -d) || exit 1
trap 'rm -rf "$tmp"' EXIT INT TERM
pass=0
fail=0

# make_java DIR VERSION: stub java; reports VERSION for -version, else records args and stdin.
make_java() {
	mkdir -p "$1"
	cat >"$1/java" <<STUB
#!/bin/sh
for a in "\$@"; do
	if [ "\$a" = "-version" ]; then
		echo "Picked up JAVA_TOOL_OPTIONS: -Dfoo=bar" >&2
		echo 'openjdk version "$2" 2025-09-16' >&2
		echo 'OpenJDK Runtime Environment (build $2+1)' >&2
		exit 0
	fi
done
for a in "\$@"; do printf '%s\n' "\$a"; done >"\$STUB_OUT"
cat >"\$STUB_IN"
printf '%s\n' "\$JDBX_CONFIG_DIR" "\$JDBX_STATE_DIR" "\$XDG_CONFIG_HOME" "\$XDG_STATE_HOME" "\$HOME" >"\$STUB_ENV"
exit \${STUB_EXIT:-0}
STUB
	chmod +x "$1/java"
}

check() { # name condition-exit-status
	if [ "$2" -eq 0 ]; then pass=$((pass + 1)); echo "ok   $1"
	else fail=$((fail + 1)); echo "FAIL $1"; fi
}

contains() { grep -F -- "$2" "$1" >/dev/null 2>&1; }

make_java "$tmp/j25" 25.0.1
make_java "$tmp/j21" 21.0.5
touch "$tmp/app.jar"
export STUB_OUT=$tmp/out STUB_IN=$tmp/in STUB_ENV=$tmp/env
unset JAVA_HOME JDBX_OPTS JDBX_JAR JDBX_JAVA
# Do not inherit the caller's config, Profiles (including locks), Session state or home.
export JDBX_CONFIG_DIR=$tmp/config JDBX_STATE_DIR=$tmp/state
export XDG_CONFIG_HOME=$tmp/xdg-config XDG_STATE_HOME=$tmp/xdg-state HOME=$tmp/home
mkdir -p "$JDBX_CONFIG_DIR" "$JDBX_STATE_DIR" "$XDG_CONFIG_HOME" "$XDG_STATE_HOME" "$HOME"

run() { # runs wrapper with minimal PATH (system tools only), stderr to $tmp/err
	env PATH=/usr/bin:/bin "$@" 2>"$tmp/err"
}

# exit code pass-through
printf 'x' | STUB_EXIT=7 JDBX_JAVA=$tmp/j25/java JDBX_JAR=$tmp/app.jar run "$wrapper" a >/dev/null 2>&1
rc=$?
[ $rc -eq 7 ]; check "exit code passes through" $?

# Every wrapper invocation uses our temporary config, Profiles, state and home.
printf '%s\n' "$tmp/config" "$tmp/state" "$tmp/xdg-config" "$tmp/xdg-state" "$tmp/home" >"$tmp/expected-env"
cmp -s "$tmp/env" "$tmp/expected-env"; check "config and state are isolated from the caller" $?

# arguments incl. spaces and quotes, with -jar
printf 'x' | JDBX_JAVA=$tmp/j25/java JDBX_JAR=$tmp/app.jar run "$wrapper" -c "select 'a b'" '"q"' ''
printf -- '-jar\n%s\n-c\nselect '"'a b'"'\n"q"\n\n' "$tmp/app.jar" >"$tmp/expected"
cmp -s "$tmp/out" "$tmp/expected"; check "arguments pass through unchanged" $?

# stdin
printf 'line1\nline2\n' | JDBX_JAVA=$tmp/j25/java JDBX_JAR=$tmp/app.jar run "$wrapper"
[ "$(cat "$tmp/in")" = "line1
line2" ]; check "stdin passes through" $?

# JDBX_OPTS before -jar
printf 'x' | JDBX_OPTS='-Xmx64m -Dx=y' JDBX_JAVA=$tmp/j25/java JDBX_JAR=$tmp/app.jar run "$wrapper" z
[ "$(sed -n 1,3p "$tmp/out" | tr '\n' ' ')" = "-Xmx64m -Dx=y -jar " ]; check "JDBX_OPTS placed before -jar" $?

# missing jar
JDBX_JAVA=$tmp/j25/java JDBX_JAR=$tmp/none.jar run "$wrapper" >/dev/null; rc=$?
[ $rc -eq 1 ] && contains "$tmp/err" "$tmp/none.jar" && contains "$tmp/err" "./mvnw -DskipTests package"
check "missing jar: message, path, hint, exit 1" $?

# too old Java
JDBX_JAVA=$tmp/j21/java JDBX_JAR=$tmp/app.jar run "$wrapper" >/dev/null; rc=$?
[ $rc -eq 127 ] && contains "$tmp/err" "25" && contains "$tmp/err" JDBX_JAVA && contains "$tmp/err" JAVA_HOME
check "too old Java: message names 25, JDBX_JAVA, JAVA_HOME, exit 127" $?

# no Java at all (PATH has no java)
mkdir -p "$tmp/emptyjh"
JAVA_HOME=$tmp/emptyjh JDBX_JAR=$tmp/app.jar run "$wrapper" >/dev/null; rc=$?
if [ -x /usr/bin/java ] || [ -x /bin/java ]; then
	echo "skip no Java (system java in /usr/bin)"
else
	[ $rc -eq 127 ] && contains "$tmp/err" JDBX_JAVA
	check "no Java: message, exit 127" $?
fi

# JAVA_HOME lookup
mkdir -p "$tmp/jh/bin"; make_java "$tmp/jh/bin" 26
printf 'x' | JAVA_HOME=$tmp/jh JDBX_JAR=$tmp/app.jar run "$wrapper" q
contains "$tmp/out" "$tmp/app.jar"; check "JAVA_HOME/bin/java is used" $?

# PATH lookup
printf 'x' | env PATH="$tmp/j25:/usr/bin:/bin" JDBX_JAR=$tmp/app.jar "$wrapper" q 2>"$tmp/err"
contains "$tmp/out" "$tmp/app.jar"; check "java on PATH is used" $?

# jar next to script, symlink from another directory
mkdir -p "$tmp/inst/bin" "$tmp/inst/lib" "$tmp/links"
cp "$wrapper" "$tmp/inst/bin/jdbx"
touch "$tmp/inst/lib/jdbx.jar"
ln -s "$tmp/inst/bin/jdbx" "$tmp/links/jdbx"
(cd / && printf 'x' | JDBX_JAVA=$tmp/j25/java run "$tmp/links/jdbx" hi)
contains "$tmp/out" "$tmp/inst/bin/../lib/jdbx.jar" || contains "$tmp/out" "$tmp/inst/lib/jdbx.jar"
check "symlink: finds ../lib/jdbx.jar from another directory" $?

# jar in target/ relative to repo root, via symlink
mkdir -p "$tmp/repo/bin" "$tmp/repo/target"
cp "$wrapper" "$tmp/repo/bin/jdbx"
touch "$tmp/repo/target/jdbx-0.0.1-SNAPSHOT.jar"
ln -s "$tmp/repo/bin/jdbx" "$tmp/links/jdbx2"
(cd / && printf 'x' | JDBX_JAVA=$tmp/j25/java run "$tmp/links/jdbx2" hi)
contains "$tmp/out" "jdbx-0.0.1-SNAPSHOT.jar"; check "symlink: finds target/jdbx-*.jar" $?

echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
