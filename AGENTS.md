# Background Scala REPL

## Purpose 

Send strings from args or stdin to a persistent background Scala REPL, avoiding the startup cost on every invocation.

## How it works

`hot-scala-repl.scala` keeps a `scala repl` process running in the background using:

- A **named pipe (FIFO)** at `~/.hot-scala-repl/input.fifo` — the REPL reads input from it. The FIFO is opened read-write (`0<>`) so the REPL never sees EOF.
- A **PID file** at `~/.hot-scala-repl/pid` — used to detect whether the REPL is still alive (`kill -0`).
- A **dir file** at `~/.hot-scala-repl/dir` — remembers the project directory the REPL was started with. If `--path` changes, the REPL is automatically killed and restarted.
- An **output log** at `~/.hot-scala-repl/output.log` — captures the REPL's stdout/stderr.

On the first invocation the REPL is started (takes ~3s). Subsequent calls detect the running process, write to the FIFO, and stream output back by polling the log file every 20ms until the `scala> ` prompt reappears.

## Usage

```bash
# Evaluate an expression (starts the REPL on first use)
./hsr 1 + 1

# Start the REPL with a project directory (gives access to project deps/sources)
./hsr --path my/projdir/path "println(42)"

# If the dir changes, the REPL is automatically restarted
./hsr --path other/project 

# Pipe from stdin
echo "val x = 42; x * x" | ./hsr

# Continuously follow REPL output
./hsr --tail

# Stop the background REPL
./hsr --kill
```

## Building a native binary

Run `./package.sh` to build a native binary. This produces a ~15 MB standalone ELF binary in `./hsr`. GraalVM (Java 21 LTS) is downloaded automatically by scala-cli. If `cs` (coursier) is on PATH, the script auto-detects the latest `graalvm-java21` version; otherwise it falls back to a hardcoded version. The binary starts instantly (~87ms for a hot REPL call). The background REPL itself still requires `scala` on PATH.

## Publishing a release

This requires write access to the repository. Run `./publish.sh` to create a GitHub release (requires `gh` CLI). It copies `hsr` to `hsr-linux-amd64`, creates a tagged release, and uploads the binary. Bump the `VERSION` variable in `publish.sh` before each release.
