# MinehuntF game flow

[简体中文](../zh-CN/README.md) · [Documentation home](../README.md)

MinehuntF hosts one game session per server process. The game manager owns the shared lifecycle, while the selected mode owns its roles, rules, win conditions, and in-game events.

The plugin currently registers and selects only [Manhunt](modes/manhunt.md). [Bingo](modes/bingo.md) has an identifier and an integration boundary, but is not playable yet.

## Flow overview

```mermaid
stateDiagram-v2
    [*] --> LOBBY
    LOBBY --> COUNTDOWN: /minehunt start
    COUNTDOWN --> LOBBY: a participant disconnects
    COUNTDOWN --> RUNNING: five-second countdown finishes
    RUNNING --> ENDING: win condition or stop vote passes
    ENDING --> FINISHED: cancel tasks and save the result
    FINISHED --> [*]: remake vote passes and stops the server
```

### 1. Lobby (LOBBY)

- On startup, the plugin initializes storage and creates the selected mode.
- A joining player is placed in Adventure mode and assigned the mode's spectator role by default.
- Players can select roles and inspect or change the current mode's rules.
- The current implementation selects Manhunt on startup and does not expose a mode-selection command.
- Players may start a remake vote. Its voter list is all players online when the vote starts; it passes at 50% within 30 seconds.

### 2. Start countdown (COUNTDOWN)

- `/minehunt start` first asks the selected mode to validate its start requirements.
- A successful validation begins a five-second countdown.
- Roles and rules cannot be changed during the countdown.
- If a participant disconnects during the countdown, it is cancelled and the game returns to the lobby.

### 3. Running game (RUNNING)

- When the countdown finishes, the system snapshots the online participants and creates the active session.
- The selected mode initializes roles and takes ownership of in-game events, mode tasks, and win detection.
- Returning participants recover their original identity; players outside the session join as spectators.
- The selected mode explicitly owns its scheduled tasks, and the manager asks it to cancel them when the game ends.
- An initial game record is written at the start, while the mode continues collecting its own data.
- Non-eliminated participants may start a stop vote. Its voter list is fixed when it starts; 80% within 30 seconds ends the game as stopped with no winner.

### 4. Ending (ENDING)

- Reaching a mode win condition or passing the stop vote begins the ending phase.
- The manager stops all per-game tasks and cancels an unfinished stop vote.
- The selected mode displays its result, restores player state, and builds the final game and player records.
- The final write waits for the initial record ID, preventing start and end writes from racing each other.

### 5. Finished (FINISHED)

- The finished session no longer accepts gameplay events.
- Players may start a remake vote. If 50% of all online players vote within 30 seconds, the server stops after five seconds.
- The current version has no command that returns directly from `FINISHED` to a fresh lobby. Automatic remakes require a launcher or process supervisor to restart the stopped server.

## Shared commands

The main command is `/minehunt`; `/mh` is its alias.

| Command | Available phase | Purpose |
| --- | --- | --- |
| `/minehunt help` | Any | Show command help. |
| `/minehunt join <role>` | Lobby | Join a role exposed by the selected mode. |
| `/minehunt leave` | Lobby | Join the selected mode's spectator role. |
| `/minehunt rule <rule>` | Any | Inspect one rule from the selected mode. |
| `/minehunt rule <rule> <value>` | Lobby | Change one rule from the selected mode. |
| `/minehunt start` | Lobby | Validate the selected mode and begin the countdown. |
| `/minehunt stop` | Running | Vote as a participant to stop the session. |
| `/minehunt give <item>` | Mode-defined | Obtain a special item exposed by the selected mode. |
| `/minehunt remake` | Lobby or finished | Vote to stop and restart the game server. |
| `/minehunt reload` | Any | Reload configuration; a player must be an operator. |

## Mode documentation

- [Manhunt](modes/manhunt.md) — implemented and selected at plugin startup.
- [Bingo](modes/bingo.md) — not implemented yet.
