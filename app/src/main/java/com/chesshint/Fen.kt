package com.chesshint

object Fen {
    private val pieceMap = mapOf(
        "♔" to "K", "♕" to "Q", "♖" to "R", "♗" to "B", "♘" to "N", "♙" to "P",
        "♚" to "k", "♛" to "q", "♜" to "r", "♝" to "b", "♞" to "n", "♟" to "p"
    )

    fun fromBoard(board: List<String>, whiteToMove: Boolean): String {
        require(board.size == 64) { "Board must contain 64 squares" }
        require(board.all { it.isEmpty() || it in pieceMap }) { "Board contains an unknown piece" }
        val ranks = (0..7).map { row ->
            var empty = 0
            buildString {
                for (col in 0..7) {
                    val fenPiece = pieceMap[board[row * 8 + col]]
                    if (fenPiece == null) empty++ else {
                        if (empty > 0) { append(empty); empty = 0 }
                        append(fenPiece)
                    }
                }
                if (empty > 0) append(empty)
            }
        }.joinToString("/")
        return "$ranks ${if (whiteToMove) "w" else "b"} - - 0 1"
    }

    fun validate(board: List<String>): String? {
        if (board.size != 64) return "Некорректный размер доски"
        if (board.any { it.isNotEmpty() && it !in pieceMap }) return "Неизвестная фигура"
        if (board.count { it == "♔" } != 1) return "На доске должен быть ровно один белый король"
        if (board.count { it == "♚" } != 1) return "На доске должен быть ровно один чёрный король"
        if (board.count { it == "♙" } > 8 || board.count { it == "♟" } > 8) return "На доске не может быть больше восьми пешек одного цвета"
        return null
    }
}
