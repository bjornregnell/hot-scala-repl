#!/usr/bin/env bash

GRAALVM_JVM_ID="graalvm-java21:21.0.2"
if command -v cs &>/dev/null; then
  latest=$(cs java --available 2>/dev/null | grep '^graalvm-java21:' | sort -V | tail -1)
  if [[ -n "$latest" ]]; then
    GRAALVM_JVM_ID="$latest"
  fi
fi
echo "Using $GRAALVM_JVM_ID"

scala-cli --power package hot-scala-repl.scala -o hsr -f --native-image \
  --graalvm-jvm-id "$GRAALVM_JVM_ID" \
  -- --no-fallback -H:+ReportExceptionStackTraces --initialize-at-build-time \
  -H:+UnlockExperimentalVMOptions -march=native
