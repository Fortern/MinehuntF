# Manhunt mode

[简体中文](../../zh-CN/modes/manhunt.md) · [Back to game flow](../README.md)

## Objectives and roles

| Role ID | Identity | Objective |
| --- | --- | --- |
| `speedrunner` | Speedrunner | Kill the Ender Dragon before every speedrunner is eliminated. |
| `hunter` | Hunter | Track down and eliminate every speedrunner. |
| `audience` | Spectator | Does not affect the outcome and uses Spectator mode during the game. |

At least one speedrunner must be online to start. The current implementation does not require a hunter.

While in the lobby, the Overworld border is reduced to 32 blocks, time, weather, and mob spawning are paused, and players wait in Adventure mode. The sidebar displays the current rules.

## Starting the game

1. Players choose an identity with `/minehunt join speedrunner`, `/minehunt join hunter`, or `/minehunt leave`.
2. `/minehunt start` validates the online speedrunners and begins a five-second countdown.
3. At game start, all online players have their inventories cleared, health, food, saturation, and levels restored, and are teleported to the Overworld spawn.
4. Speedrunners immediately enter Survival mode.
5. Hunters wait temporarily in Spectator mode and cannot move. After `hunter_ready_cd` seconds, they are teleported to spawn, enter Survival mode, and receive a tracking compass.
6. Daylight, weather, and mob spawning resume, difficulty is set to Hard, and the Overworld border expands to 9,999,999 blocks.

## During the game

### Speedrunners

- Follow the normal survival progression, gather resources, enter the Nether and the End, and kill the Ender Dragon.
- A dead speedrunner is permanently eliminated and enters Spectator mode.
- Hunters win when the final speedrunner is eliminated.
- Speedrunners win when the Ender Dragon dies.

### Hunters

- A hunter's compass refreshes its target position every five ticks.
- In the same dimension, it points to the target's current position.
- Across dimensions, a compass in the Overworld or Nether points to the most recently recorded portal position; there is no cross-dimensional target in the End.
- Attempting to drop the special compass cancels the drop and selects the next online, non-eliminated speedrunner.
- A dead hunter temporarily enters Spectator mode and returns to Survival after `hunter_respawn_cd` seconds.
- Hunters receive a replacement special compass on respawn and may also use `/minehunt give compass` during the game.

### Disconnecting and returning

- The participant list is fixed when the game starts.
- Returning participants recover their original faction; other players enter as spectators.
- An offline speedrunner remains part of the session. The compass retains the last usable tracking position, while target switching prefers non-eliminated speedrunners who are online.

## Mode rules

Rules can only be changed in the lobby. For example:

```text
/minehunt rule hunter_ready_cd 15
/minehunt rule friendly_fire false
```

| Rule | Default | Accepted values | Effect |
| --- | ---: | --- | --- |
| `hunter_ready_cd` | `30` | `0`–`120` seconds | How long hunters wait at the beginning. |
| `hunter_respawn_cd` | `30` | `0`–`120` seconds | How long a dead hunter waits before returning. |
| `friendly_fire` | `true` | `true` / `false` | Whether players in the same faction can damage each other. |
| `hunter_intentional` | `false` | `true` / `false` | Whether hunters may use beds or respawn anchors for intentional game-design explosions. Speedrunners are unaffected. |
| `speedrun_loot_up` | `true` | `true` / `false` | Improves access to blaze rods and ender pearls through Blaze and Enderman drops and Piglin bartering. |

Command completion recommends `0`, `15`, and `30` for the two countdown rules, but every integer from `0` through `120` is valid.

## Ending and records

- Ender Dragon death: speedrunners win normally.
- Every speedrunner dies: hunters win normally.
- Stop vote passes: the game is marked as stopped with no winner.
- At the end, all online players return to Survival mode and see the winner and game-result sidebar.
- Records include faction membership, world seeds, start and end times, the first players and times to enter the Nether and End, and per-player combat, resource, and item statistics.
