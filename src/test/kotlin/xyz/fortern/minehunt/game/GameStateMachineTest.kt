package xyz.fortern.minehunt.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GameStateMachineTest {
    @Test
    fun `normal lifecycle reaches finished`() {
        val state = GameStateMachine()

        state.transitionTo(GamePhase.COUNTDOWN)
        state.transitionTo(GamePhase.RUNNING)
        state.transitionTo(GamePhase.ENDING)
        state.transitionTo(GamePhase.FINISHED)

        assertEquals(GamePhase.FINISHED, state.phase)
    }

    @Test
    fun `cancelled countdown returns to lobby`() {
        val state = GameStateMachine()

        state.transitionTo(GamePhase.COUNTDOWN)
        state.transitionTo(GamePhase.LOBBY)

        assertEquals(GamePhase.LOBBY, state.phase)
    }

    @Test
    fun `invalid transition is rejected without changing phase`() {
        val state = GameStateMachine()

        assertThrows(IllegalArgumentException::class.java) {
            state.transitionTo(GamePhase.RUNNING)
        }
        assertEquals(GamePhase.LOBBY, state.phase)
    }
}
