package com.chesshint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FenTest {
    @Test fun initialPositionFenIsCorrect() {
        val board = listOf(
            "♜","♞","♝","♛","♚","♝","♞","♜",
            "♟","♟","♟","♟","♟","♟","♟","♟",
            "","","","","","","","",
            "","","","","","","","",
            "","","","","","","","",
            "","","","","","","","",
            "♙","♙","♙","♙","♙","♙","♙","♙",
            "♖","♘","♗","♕","♔","♗","♘","♖"
        )
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", Fen.fromBoard(board, true))
        assertNull(Fen.validate(board))
    }

    @Test fun rejectsMissingKings() {
        val board = List(64) { "" }
        assertNotNull(Fen.validate(board))
    }

    @Test fun emitsBlackToMove() {
        val board = MutableList(64) { "" }
        board[4] = "♚"
        board[60] = "♔"
        assertEquals("4k3/8/8/8/8/8/8/4K3 b - - 0 1", Fen.fromBoard(board, false))
    }
}
