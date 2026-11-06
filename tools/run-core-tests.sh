#!/usr/bin/env bash
# Compiles app/src/main/.../core + app/src/test with the Kotlin compiler bundled inside a
# Gradle distribution and runs the tests with a tiny JUnit shim. No network, no Android SDK.
set -euo pipefail
cd "$(dirname "$0")/.."
GRADLE_LIB="${GRADLE_LIB:-$(dirname "$(readlink -f "$(command -v gradle)")")/../lib}"
STDLIB=$(ls "$GRADLE_LIB"/kotlin-stdlib-[0-9]*.jar | head -1)
CP=$(ls "$GRADLE_LIB"/*.jar | tr '\n' ':')
OUT=$(mktemp -d)
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -nowarn \
  -cp "$STDLIB" -d "$OUT" \
  app/src/main/java/capital/cannuran/pulse/core/*.kt app/src/test/java/capital/cannuran/pulse/core/*.kt tools/localtest/*.kt
java -cp "$OUT:$STDLIB" localtest.RunnerKt "$OUT"
