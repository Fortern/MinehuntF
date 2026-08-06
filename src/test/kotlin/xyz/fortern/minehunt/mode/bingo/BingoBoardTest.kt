package xyz.fortern.minehunt.mode.bingo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BingoBoardTest {
    @Test
    fun `target pool has enough unique materials in every tier`() {
        val allTargets = BingoTargetPool.tiers.flatten()

        assertTrue(BingoTargetPool.tiers.all { it.size >= BingoBoard.SIZE })
        assertEquals(allTargets.size, allTargets.toSet().size)
    }

    @Test
    fun `same seed generates the same unique card`() {
        val first = BingoBoard.generate(42L)
        val second = BingoBoard.generate(42L)

        assertEquals(first, second)
        assertEquals(BingoBoard.SLOT_COUNT, first.targets.size)
        assertEquals(BingoBoard.SLOT_COUNT, first.targets.toSet().size)
    }

    @Test
    fun `every winning line contains one target from every difficulty tier`() {
        val board = BingoBoard.generate(7L)

        BingoBoard.LINES.forEach { line ->
            assertEquals(
                setOf(0, 1, 2, 3, 4),
                line.map { BingoTargetPool.tierOf(board.targets[it]) }.toSet(),
            )
        }
    }

    @Test
    fun `rows columns and diagonals are detected only when complete`() {
        val board = BingoBoard.generate(123L)
        val row = BingoBoard.LINES[0]
        val column = BingoBoard.LINES[5]
        val diagonal = BingoBoard.LINES[10]

        assertFalse(board.winningLines(row.take(4).mapTo(mutableSetOf()) { board.targets[it] }).isNotEmpty())
        assertTrue(row in board.winningLines(row.mapTo(mutableSetOf()) { board.targets[it] }))
        assertTrue(column in board.winningLines(column.mapTo(mutableSetOf()) { board.targets[it] }))
        assertTrue(diagonal in board.winningLines(diagonal.mapTo(mutableSetOf()) { board.targets[it] }))
    }
}
