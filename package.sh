#!/usr/bin/env bash
scala-cli --power package hot-scala-repl.scala -o hsr -f --native-image \
  --graalvm-jvm-id graalvm-java25:25.0.1 \
  -- --no-fallback -H:+ReportExceptionStackTraces --initialize-at-build-time \
  -H:+UnlockExperimentalVMOptions --future-defaults=all -march=native
