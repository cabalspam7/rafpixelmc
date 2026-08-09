# Hypixel Clone

PaperMC 1.21.4 private Minecraft server replicating [hypixel.net/play](https://hypixel.net/play) — **19 games** + **5 features**, all verified via a real `mineflayer` client.

## Status

- 19/19 games from hypixel.net/play — implemented & tested
- 5/5 core features — Lobby, Parties, Friends, Guilds, Coins
- Lobby polish: hub world, 19 game NPCs, sidebar scoreboard, hub portal

## Games (19)

| # | Game | Plugin |
|---|------|--------|
| 1 | SkyWars | `SkyWarsPlugin` (custom) |
| 2 | Duels | `DuelsPlugin` (custom, 8 kits) |
| 3 | BedWars | `bedwars-source` (ScreamingBedWars) |
| 4 | SkyBlock | BentoBox 3.7.1 + CaveBlock 1.20.1 |
| 5 | Build Battle | `BuildBattlePlugin` (custom) |
| 6 | Murder Mystery | `MurderMysteryPlugin` (custom) |
| 7 | The Walls | `TheWallsPlugin` (custom, 4 teams) |
| 8 | Mega Walls | `MegaWallsPlugin` (custom, 4 classes) |
| 9 | Arcade | `HypixelMore` (bulk engine) |
| 10 | Blitz Survival Games | `HypixelMore` |
| 11 | Smash Heroes | `HypixelMore` |
| 12 | TNT Games | `HypixelMore` |
| 13 | Turbo Kart Racers | `HypixelMore` |
| 14 | UHC Champions | `HypixelMore` |
| 15 | VampireZ | `HypixelMore` |
| 16 | Warlords | `HypixelMore` |
| 17 | Arena Brawl | `HypixelMore` |
| 18 | Paintball | `HypixelMore` |
| 19 | Quakecraft | `HypixelMore` |

## Features (5)

- **Lobby** — spawn hub with 19 game NPCs (item frames + player heads), scoreboard sidebar, hub portal (gold pressure plate) + `/lobby` command
- **Coins** — cross-game persistence (`CoinsPlugin`, shaded Gson, `ServicesManager` API)
- **Friends** — `SocialPlugin`
- **Parties** — `SocialPlugin`
- **Guilds** — `GuildPlugin`

## How to play

1. Server runs PaperMC 1.21.4, `online-mode=false`, port `25565`, RCON `25575`.
2. Join lobby → click a game NPC **or** run `/play <game>` → teleports into the game.
3. Return to lobby via the gold hub portal or `/lobby`.

## Architecture

```
CoinsPlugin     cross-game coin economy + API
SocialPlugin    friends + parties
GuildPlugin     guilds
HypixelCore     core hooks (scoreboard, welcome)
SkyWarsPlugin   custom SkyWars (radial islands)
DuelsPlugin     custom 1v1/2v2 duels (8 kits)
BuildBattlePlugin / MurderMysteryPlugin / TheWallsPlugin / MegaWallsPlugin
HypixelMore     generic engine for 11 bulk game modes
LobbyPlugin     hub, NPCs, scoreboard, portal
```

All plugins are Maven projects (`pom.xml` + `src/main/java`). Build with `mvn package`, drop `target/*.jar` into `server/plugins/`.

## Verification

Every game + feature was exercised by a headless `mineflayer` (`mineflayer@4`, Node 22) bot that actually joined, spawned, and played rounds — not simulated. See `server/` runtime (excluded from this repo via `.gitignore`).

## License

MIT — see [LICENSE](LICENSE).
