package xyz.fortern.minehunt.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class LobbyTest {
    @Test
    fun `assigning a new role replaces the old membership`() {
        val lobby = Lobby()
        val player = UUID.randomUUID()

        lobby.assign(player, "Player", "audience")
        lobby.assign(player, "Player", "hunter")

        assertEquals("hunter", lobby.member(player)?.role)
        assertEquals(emptyList<LobbyMember>(), lobby.members("audience"))
        assertEquals(listOf(player), lobby.members("hunter").map { it.uniqueId })
    }

    @Test
    fun `removing a member clears the lobby entry`() {
        val lobby = Lobby()
        val player = UUID.randomUUID()
        lobby.assign(player, "Player", "speedrunner")

        lobby.remove(player)

        assertNull(lobby.member(player))
    }
}
