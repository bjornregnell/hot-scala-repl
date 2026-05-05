# Background Scala REPL

## Purpose 

Send strings from args or stdin to a persistent background Scala REPL, avoiding the startup cost on every invocation.

## How it works

`hot-scala-repl.scala` keeps a `scala repl` process running in the background using:

- A **named pipe (FIFO)** at `~/-hot-scala-repl/input.fifo` — the REPL reads input from it. The FIFO is opened read-write (`0<>`) so the REPL never sees EOF.
- A **PID file** at `~/-hot-scala-repl/pid` — used to detect whether the REPL is still alive (`kill -0`).
- A **dir file** at `~/-hot-scala-repl/dir` — remembers the project directory the REPL was started with. If `--path` changes, the REPL is automatically killed and restarted.
- An **output log** at `~/-hot-scala-repl/output.log` — captures the REPL's stdout/stderr.

On the first invocation the REPL is started (takes ~3s). Subsequent calls detect the running process, write to the FIFO, and stream output back by polling the log file every 20ms until the `scala> ` prompt reappears.

## Usage

```bash
# Evaluate an expression (starts the REPL on first use)
./hsr 1 + 1

# Start the REPL with a project directory (gives access to project deps/sources)
./hsr --path my/projdir/path "println(42)"

# If the dir changes, the REPL is automatically restarted
./hsr --path other/project "println(99)"

# Pipe from stdin
echo "val x = 42; x * x" | ./hsr

# Continuously follow REPL output
./hsr --tail

# Stop the background REPL
./hsr --kill
```

## Building a native binary

See README.md for how to build a native binary

```bash
scala-cli --power package hot-scala-repl.scala -o r -f --native-image \
  --graalvm-jvm-id graalvm-java25:25.0.1 \
  -- --no-fallback -H:+ReportExceptionStackTraces --initialize-at-build-time
```

This produces a ~15 MB standalone ELF binary in `./hsr`. GraalVM is downloaded automatically by scala-cli. The binary starts instantly (~87ms for a hot REPL call). The background REPL itself still requires `scala` on PATH.
