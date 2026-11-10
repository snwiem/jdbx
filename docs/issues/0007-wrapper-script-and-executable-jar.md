# 0007: Wrapper script and executable jar

**What to build:** The build produces one executable jar and a small `dbui` wrapper script that starts it with the arguments given, so that users do not type `java -jar`.

**Blocked by:** 0002 Batch tracer

**Status:** done

**Parent:** `docs/prd/mvp-1.0.md`

- [x] The script passes all arguments and stdin through unchanged and returns the exit code of the jar
- [x] The script reports a clear message when no suitable Java is found
- [x] The build documents how to install the wrapper (README section)
- [x] Starting the script with `--help` works from any directory
