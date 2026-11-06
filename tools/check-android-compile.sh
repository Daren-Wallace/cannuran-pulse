#!/usr/bin/env bash
# Type-checks the whole app (core + Android layer) against tools/androidstubs on a plain JVM.
# Catches typos, wrong signatures between our own classes, nullability and smart-cast errors
# without the Android SDK. It does NOT replace the real Gradle build in CI.
set -euo pipefail
cd "$(dirname "$0")/.."
GRADLE_LIB="${GRADLE_LIB:-$(dirname "$(readlink -f "$(command -v gradle)")")/../lib}"
STDLIB=$(ls "$GRADLE_LIB"/kotlin-stdlib-[0-9]*.jar | head -1)
COROUTINES=$(ls "$GRADLE_LIB"/kotlinx-coroutines-core-jvm-*.jar | head -1)
CP=$(ls "$GRADLE_LIB"/*.jar | tr '\n' ':')
OUT=$(mktemp -d)
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
  -cp "$STDLIB:$COROUTINES" -d "$OUT" \
  $(find app/src/main/java -name "*.kt") tools/androidstubs/*.kt
echo "Android layer type-checks against stubs ✓"
