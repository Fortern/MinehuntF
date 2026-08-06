# Bingo mode

[简体中文](../../zh-CN/modes/bingo.md) · [Back to game flow](../README.md)

Bingo is a survival item race in which the red and blue teams share one 5×5 card. The first team to complete any row, column, or diagonal wins.

## Selecting the mode and teams

The server still selects Manhunt on startup. An operator may run `/minehunt mode bingo` in the lobby; switching resets every online player to the spectator role.

| Role | Team | Purpose |
| --- | --- | --- |
| `red` | Red | Collects targets and shares Red progress. |
| `blue` | Blue | Collects targets and shares Blue progress. |
| `audience` | Spectator | Does not trigger targets and uses Spectator mode during the game. |

Use `/minehunt join red` or `/minehunt join blue`. Both teams need at least one online player before the game can start.

## Rules

| Rule | Default | Purpose |
| --- | --- | --- |
| `card_seed` | `0` | A nonzero value deterministically generates the same card; `0` chooses a random seed at game start. |
| `keep_inventory` | `true` | Keeps inventory and experience on death; when disabled, the card is still restored after respawning. |
| `pvp` | `false` | Allows damage between Red and Blue; friendly fire is always disabled. |

## Card generation

- The card is always 5×5 with 25 unique item targets and no free square.
- The pool has five difficulty tiers, and five targets are selected from each tier.
- A Latin-square layout makes every row, column, and diagonal contain exactly one target from every difficulty tier.
- Both teams always receive the same card and card seed.

## Starting

When the countdown ends, the mode snapshots both online teams, clears every online player's inventory, and teleports everyone to the Overworld spawn. Participants enter Survival mode, spectators enter Spectator mode, and every online player receives an undroppable `Bingo Card`.

Right-click the card to open its 5×5 inventory view. Each square shows Red and Blue completion separately. `/minehunt give card` restores a missing card.

## Completing targets and winning

- The plugin scans online participant inventories every tick, so pickups, crafting, smelting, trades, container transfers, and every other source that actually puts an item in an inventory use one rule.
- The first possession of a target permanently completes that square for the player's team and records the contributor and elapsed time.
- Using, losing, or dropping the item later never revokes a completed square.
- Red and Blue may complete the same target independently; duplicates do not score again.
- Every completion checks all five rows, five columns, and two diagonals. The first team to complete one wins.
- If both teams form a line during the same inventory scan, the game is a draw.

## Death, disconnects, and reconnects

- Death does not eliminate a participant. Respawning restores Survival mode and the card.
- Disconnecting during the game keeps the player's team and contributions.
- A returning participant recovers the team fixed at game start; a new player joins as a spectator.
- A participant disconnecting during the countdown cancels it and returns the session to the lobby.

## Results and persistence

The mode stores the card seed, all 25 targets, every claim's team/player/slot/elapsed time, winning lines, and each player's contributed targets. SQLite, MySQL, and PostgreSQL use a dedicated `bingo_record` details table; a failed or timed-out database save still uses the shared local-file fallback.

A passed stop vote ends with no winner. A normal line finish shows the winner, both completion counts, and total duration on the result sidebar.
