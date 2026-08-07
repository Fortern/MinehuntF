package xyz.fortern.minehunt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import xyz.fortern.minehunt.game.VoteBallot
import xyz.fortern.minehunt.game.VoteResult
import java.util.*

class VoteBallotTest {
    @Test
    fun `a new vote clears votes from the previous ballot`() {
        val players = List(2) { UUID.randomUUID() }
        val ballot = VoteBallot(1.0f)

        ballot.start(players)
        assertEquals(VoteResult.ACCEPTED, ballot.vote(players[0]))
        ballot.cancel()
        ballot.start(players)

        assertEquals(0, ballot.votes)
        assertTrue(ballot.running)
    }

    @Test
    fun `duplicate vote is reported without increasing the vote count`() {
        val players = List(2) { UUID.randomUUID() }
        val ballot = VoteBallot(1.0f)
        ballot.start(players)

        assertEquals(VoteResult.ACCEPTED, ballot.vote(players[0]))
        assertEquals(VoteResult.DUPLICATED, ballot.vote(players[0]))
        assertEquals(1, ballot.votes)
        assertTrue(ballot.running)
    }

    @Test
    fun `ineligible vote is rejected without increasing the vote count`() {
        val player = UUID.randomUUID()
        val ballot = VoteBallot(1.0f)
        ballot.start(listOf(player))

        assertEquals(VoteResult.REJECTED, ballot.vote(UUID.randomUUID()))
        assertEquals(0, ballot.votes)
        assertTrue(ballot.running)
        assertEquals(VoteResult.PASSED, ballot.vote(player))
    }

    @Test
    fun `reaching the configured ratio passes the vote`() {
        val players = List(5) { UUID.randomUUID() }
        val ballot = VoteBallot(0.6f)
        ballot.start(players)

        assertEquals(VoteResult.ACCEPTED, ballot.vote(players[0]))
        assertEquals(VoteResult.ACCEPTED, ballot.vote(players[1]))
        assertEquals(VoteResult.PASSED, ballot.vote(players[2]))
        assertEquals(3, ballot.votes)
        assertFalse(ballot.running)
    }

    @Test
    fun `passing threshold rounds up to the next whole vote`() {
        val players = List(3) { UUID.randomUUID() }
        val ballot = VoteBallot(0.6f)
        ballot.start(players)

        assertEquals(VoteResult.ACCEPTED, ballot.vote(players[0]))
        assertEquals(VoteResult.PASSED, ballot.vote(players[1]))
    }

    @Test
    fun `duplicate players count only once in the electorate`() {
        val player = UUID.randomUUID()
        val ballot = VoteBallot(1.0f)

        ballot.start(listOf(player, player))

        assertEquals(1, ballot.players)
        assertEquals(VoteResult.PASSED, ballot.vote(player))
    }

    @Test
    fun `invalid rates are rejected`() {
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, -0.1f, 0.0f, 1.1f, Float.POSITIVE_INFINITY).forEach { rate ->
            assertThrows(IllegalArgumentException::class.java) { VoteBallot(rate) }
        }
    }

    @Test
    fun `a ballot requires voters and cannot be restarted while running`() {
        val player = UUID.randomUUID()
        val ballot = VoteBallot(1.0f)

        assertThrows(IllegalArgumentException::class.java) { ballot.start(emptyList()) }
        ballot.start(listOf(player))
        assertThrows(IllegalStateException::class.java) { ballot.start(listOf(player)) }
    }

    @Test
    fun `voting requires a running ballot`() {
        val ballot = VoteBallot(1.0f)

        assertThrows(IllegalStateException::class.java) { ballot.vote(UUID.randomUUID()) }
    }
}
