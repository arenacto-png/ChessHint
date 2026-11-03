package com.chesshint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** UCI client for the Stockfish executable packaged in Android's nativeLibraryDir. */
class StockfishUci(private val engineFile: File) {
    suspend fun bestMove(fen: String, depth: Int = 14): String = withContext(Dispatchers.IO) {
        require(depth in 1..30) { "Depth must be between 1 and 30" }
        require(engineFile.isFile && engineFile.canExecute()) { "Stockfish engine is missing or not executable" }
        withTimeout(45_000) {
            val process = ProcessBuilder(engineFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val lines = LinkedBlockingQueue<String>()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val readerThread = thread(name = "stockfish-output", isDaemon = true) {
                try { while (true) lines.put(reader.readLine() ?: break) } catch (_: Exception) { }
            }
            fun send(command: String) { writer.write(command); writer.newLine(); writer.flush() }
            fun waitForLine(marker: String, timeoutSeconds: Long): String {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                while (System.nanoTime() < deadline) {
                    val line = lines.poll(250, TimeUnit.MILLISECONDS) ?: continue
                    if (line.contains(marker)) return line
                }
                error("Stockfish timed out waiting for $marker")
            }
            try {
                send("uci")
                waitForLine("uciok", 10)
                send("isready")
                waitForLine("readyok", 10)
                send("setoption name Threads value 2")
                send("setoption name Hash value 64")
                send("position fen $fen")
                send("go depth $depth")
                val line = waitForLine("bestmove", 35)
                val move = line.trim().split(Regex("\\s+")).getOrNull(1).orEmpty()
                check(move.isNotBlank() && move != "(none)" && move != "0000") { "No legal move found for this position" }
                move
            } finally {
                try { send("quit") } catch (_: Exception) { }
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
                try { reader.close() } catch (_: Exception) { }
                try { writer.close() } catch (_: Exception) { }
                readerThread.interrupt()
            }
        }
    }
}
