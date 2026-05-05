//> using scala 3.8.3
//> using toolkit 0.9.2

import java.io.{FileWriter, RandomAccessFile}

/** Directory for all hot-scala-repl state: pid file, FIFO, and output log. */
val stateDir = os.home / ".hot-scala-repl"

/** Stores the PID of the background REPL process. */
val pidFile  = stateDir / "pid"

/** Named pipe (FIFO) used to feed input to the REPL's stdin. */
val fifoPath = stateDir / "input.fifo"

/** Log file capturing the REPL's stdout and stderr. */
val logPath  = stateDir / "output.log"

/** Max time to wait for the REPL's first `scala> ` prompt. */
val StartupTimeoutMillis = 30000

/** Stores the project directory the current REPL was started with. */
val dirFile  = stateDir / "dir"

/** Checks whether the background REPL process is alive via kill -0. */
def isReplProcessRunning(): Boolean =
  os.exists(pidFile) &&
    os.read(pidFile).trim.toLongOption.exists: pid =>
      os.proc("kill", "-0", pid.toString).call(check = false).exitCode == 0

/** Returns the saved project directory, or None if the REPL was started without one. */
def savedDir(): Option[String] =
  if os.exists(dirFile) then Some(os.read(dirFile).trim).filter(_.nonEmpty) else None

extension (s: String) def wrapInSingleQuotes: String = { val q = s.replace("'", "'\\''"); s" '$q'" }

/** Creates the FIFO, launches `scala repl [dir]` in the background, and waits for it to be ready. */
def startReplProcess(dir: String): Unit =
  os.makeDir.all(stateDir)
  if os.exists(fifoPath) then os.remove(fifoPath)
  os.proc("mkfifo", fifoPath.toString).call()
  os.write.over(logPath, "")
  os.write.over(dirFile, dir)
  val dirArg = if dir.isEmpty then "" else dir.wrapInSingleQuotes

  // Open FIFO read-write (0<>) so the REPL never sees EOF
  os.proc("bash", "-c",
    s"""|nohup scala repl$dirArg 0<>"$fifoPath" >>"$logPath" 2>&1 &
        |echo $$! > "$pidFile"""".stripMargin
  ).call()
  
  val pid = os.read(pidFile).trim
  System.err.println(s"Waiting for REPL to start (pid $pid)...")
  val deadline = System.currentTimeMillis() + StartupTimeoutMillis
  while !os.read(logPath).contains("scala> ") do
    if System.currentTimeMillis() > deadline then
      System.err.println("Timed out waiting for REPL prompt.")
      return
    Thread.sleep(100)
  System.err.println("REPL ready.")

/** Update project dir, restarting if the dir changed. */
def updateDir(newDir: String): Unit =
  if savedDir() != Some(newDir) then 
    killReplProcess()
    startReplProcess(newDir)
  else
    System.err.println(s"Project unchanged.")

/** Sends lines to the REPL via the FIFO and streams output until the `scala> ` prompt returns. */
def sendToReplProcessStdIn(lines: Seq[String]): Unit =
  if !isReplProcessRunning() then startReplProcess(savedDir().getOrElse(""))
  val sizeBefore = if os.exists(logPath) then os.size(logPath) else 0L
  val writer = FileWriter(fifoPath.toIO)
  try
    lines.foreach(l => writer.write(l + "\n"))
    writer.flush()
  finally writer.close()
  val raf = RandomAccessFile(logPath.toIO, "r")
  try
    raf.seek(sizeBefore)
    var buf = ""
    var seenOutput = false
    var quietSince = System.currentTimeMillis()
    var i = 0
    while i < 500 do
      i += 1
      Thread.sleep(20)
      val avail = (raf.length() - raf.getFilePointer).toInt
      if avail > 0 then
        seenOutput = true
        quietSince = System.currentTimeMillis()
        val bytes = new Array[Byte](avail)
        raf.readFully(bytes)
        val text = String(bytes, "UTF-8")
        buf += text
        val promptIdx = buf.lastIndexOf("scala> ")
        if promptIdx >= 0 then
          print(buf.substring(0, promptIdx))
          System.out.flush()
          return
        val safeEnd = buf.length - "scala> ".length
        if safeEnd > 0 then
          print(buf.substring(0, safeEnd))
          System.out.flush()
          buf = buf.substring(safeEnd)
      else if seenOutput && System.currentTimeMillis() - quietSince > 2000 then
        print(buf)
        System.out.flush()
        return
  finally raf.close()

/** Kills the background REPL process and removes the FIFO and pid file. */
def killReplProcess(): Unit =
  if os.exists(pidFile) then
    val pid = os.read(pidFile).trim
    os.proc("kill", pid).call(check = false)
    os.remove(pidFile)
    System.err.println(s"Killed REPL process $pid")
  else
    System.err.println("No REPL process running")
  if os.exists(fifoPath) then os.remove(fifoPath)

/** Entry point. Dispatches --kill, --tail, --path, stdin, or args to the background REPL. */
@main def hotScalaRepl(args: String*): Unit =
  args match
    case Seq("--kill") =>
      killReplProcess()
    case Seq("--tail") =>
      if os.exists(logPath) then
        os.proc("tail", "-f", logPath.toString).call(stdout = os.Inherit)
      else
        System.err.println("No output log found. Start the REPL first.")
    case "--path" +: rest =>
      val newPath = rest.mkString(" ")
      println(s"Old path: ${savedDir()}\nNew path: $newPath")
      updateDir(newPath)
    case _ =>
      sendToReplProcessStdIn(Seq(args.mkString(" ")))
