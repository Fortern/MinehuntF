# Bingo mode

[简体中文](../../zh-CN/modes/bingo.md) · [Back to game flow](../README.md)

## Current status

Bingo is **not implemented and cannot be selected or started in the current version**.

The codebase defines a `BINGO` mode ID, and the shared game framework can register another mode factory. However, plugin startup currently registers only Manhunt. Bingo has no runtime implementation, listeners, roles, rules, special items, win detection, or mode-specific record details.

## Established shared flow

Once implemented, Bingo will reuse this lifecycle:

1. Select roles and configure Bingo rules in the lobby.
2. Let the mode validate its participants and configuration before the five-second countdown.
3. Snapshot participants and create a per-game task scope when the countdown finishes.
4. Let the Bingo mode generate objectives, process completion events, and determine a winner.
5. Cancel mode tasks and save shared records plus Bingo-specific details when the game ends.
6. Use participant stop voting during the game and all-online-player remake voting after it ends.

## Still to be defined

Before Bingo can be implemented, the project must decide:

- whether roles represent solo players, individual racers, or competing teams;
- card size, objective pools, and random-generation rules;
- whether obtaining, crafting, or picking up an item completes an objective;
- line, full-card, score-limit, or timed win conditions;
- handling for duplicate objectives, death, disconnects, and reconnects;
- mode rules, special items, scoreboard presentation, and persistence fields.

None of these mechanics currently has an established implementation, so this document does not present them as supported behavior.
