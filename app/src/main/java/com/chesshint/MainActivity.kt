package com.chesshint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Canvas = Color(0xFFF4F5F0)
private val Ink = Color(0xFF18211D)
private val Green = Color(0xFF315B46)
private val LightSquare = Color(0xFFE9E5D8)
private val DarkSquare = Color(0xFF78927C)
private val SelectColor = Color(0xFFEDC85D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChessHintScreen() }
    }
}

@Composable
private fun ChessHintScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf(startBoard()) }
    var selectedPiece by remember { mutableStateOf("♙") }
    var whiteToMove by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Выставьте позицию с реальной доски") }
    var busy by remember { mutableStateOf(false) }
    var bestMove by remember { mutableStateOf<String?>(null) }
    val pieces = listOf("♔","♕","♖","♗","♘","♙","♚","♛","♜","♝","♞","♟", "")

    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Canvas, surface = Color.White, onSurface = Ink)) {
        Column(Modifier.fillMaxSize().background(Canvas).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CHESSHINT", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Подсказка для вашей партии", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                Surface(shape = CircleShape, color = Color.White) {
                    Text("♟", modifier = Modifier.padding(10.dp), fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ход:", color = Ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = whiteToMove, onClick = { whiteToMove = true; bestMove = null }, label = { Text("Белые") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = !whiteToMove, onClick = { whiteToMove = false; bestMove = null }, label = { Text("Чёрные") })
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().aspectRatio(1f)) {
                for (r in 0..7) Row(Modifier.weight(1f)) {
                    for (c in 0..7) {
                        val index = r * 8 + c
                        val isLight = (r + c) % 2 == 0
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .background(if (bestMove?.take(2) == squareName(index) || bestMove?.drop(2) == squareName(index)) SelectColor else if (isLight) LightSquare else DarkSquare)
                            .clickable {
                                board = board.toMutableList().also { it[index] = selectedPiece }
                                bestMove = null
                                status = "Позиция обновлена"
                            }, contentAlignment = Alignment.Center) {
                            if (c == 0) Text("${8-r}", Modifier.align(Alignment.TopStart).padding(2.dp), fontSize = 9.sp,
                                color = if (isLight) DarkSquare else LightSquare)
                            if (r == 7) Text("abcdefgh"[c].toString(), Modifier.align(Alignment.BottomEnd).padding(2.dp), fontSize = 9.sp,
                                color = if (isLight) DarkSquare else LightSquare)
                            Text(board[index], fontSize = 31.sp, color = Ink)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                pieces.forEach { piece ->
                    Text(if (piece.isEmpty()) "⌫" else piece, fontSize = 25.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (selectedPiece == piece) Color(0xFFE3EEE3) else Color.Transparent)
                        .clickable { selectedPiece = piece }.padding(horizontal = 3.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { board = List(64) { "" }; bestMove = null; status = "Доска очищена" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Очистить")
                }
                OutlinedButton(onClick = { board = startBoard(); whiteToMove = true; bestMove = null; status = "Начальная позиция" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Сбросить")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (busy) return@Button
                busy = true
                bestMove = null
                status = "Stockfish анализирует…"
                scope.launch {
                    try {
                        val fen = Fen.fromBoard(board, whiteToMove)
                        val invalidPosition = Fen.validate(board)
                        if (invalidPosition != null) error(invalidPosition)
                        val engine = StockfishUci(File(context.applicationInfo.nativeLibraryDir, "libstockfish.so"))
                        val move = engine.bestMove(fen, 16)
                        bestMove = move
                        status = "Лучший ход: ${move.take(2)} → ${move.drop(2).take(2)}${move.getOrNull(4)?.let { "=$it" }.orEmpty()}"
                    } catch (e: Exception) {
                        status = e.message ?: "Не удалось запустить Stockfish"
                    } finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Анализ…" else "Найти лучший ход", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(status, Modifier.padding(12.dp), color = Ink, fontSize = 13.sp)
            }
            Text("Выберите фигуру, затем клетку. ⌫ удаляет фигуру · Stockfish анализирует локально",
                color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

private fun squareName(index: Int): String {
    val file = "abcdefgh"[index % 8]
    val rank = 8 - index / 8
    return "$file$rank"
}
private fun startBoard(): List<String> = listOf(
    "♜","♞","♝","♛","♚","♝","♞","♜",
    "♟","♟","♟","♟","♟","♟","♟","♟",
    "","","","","","","","",
    "","","","","","","","",
    "","","","","","","","",
    "","","","","","","","",
    "♙","♙","♙","♙","♙","♙","♙","♙",
    "♖","♘","♗","♕","♔","♗","♘","♖"
)
