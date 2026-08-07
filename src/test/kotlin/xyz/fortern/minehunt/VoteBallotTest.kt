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
    fun `duplicate and ineligible votes are rejected`() {
        val player = UUID.randomUUID()
        val ballot = VoteBallot(1.0f)
        ballot.start(listOf(player))

        assertEquals(VoteResult.PASSED, ballot.vote(player))
        assertFalse(ballot.running)

        ballot.start(listOf(player))
        assertEquals(VoteResult.REJECTED, ballot.vote(UUID.randomUUID()))
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
}
