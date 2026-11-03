# BetaHub Bug Reporter for Paper

Players type `/bug`, describe the problem in a native Minecraft dialog, and the report lands in your
[BetaHub](https://betahub.io) project with their position, world, versions and more attached automatically.
The player gets a clickable link to the new issue.

## Supported servers

| | |
|---|---|
| Server software | Paper and Paper forks (e.g. Purpur). Not Spigot (dialogs are a Paper API). Not Folia. |
| Minecraft | 1.21.11 and newer, including 26.x. Tested on 1.21.11 (Java 21) and 26.3 (Java 25). |
| Java | 21 or newer (whatever your Paper version requires) |
| Optional | [ViaVersion](https://hangar.papermc.io/ViaVersion/ViaVersion) on the same server, for the player's client version |

In a Velocity (or BungeeCord) network, install the plugin on **every backend server**, not on the proxy:
only the backend knows where the player is.

## Install

1. Copy `BetaHubBugReporter-<version>.jar` into each backend's `plugins/` folder and start the server once.
   It creates `plugins/BetaHubBugReporter/config.yml`.
2. Create a project auth token in BetaHub (next section).
3. In `config.yml`, set `betahub.project-id`, `betahub.token` and `server-name` (e.g. `survival`, `lobby`;
   the backend cannot learn its network name by itself).
4. Run `/bugreport reload`, then `/bugreport test`. The test checks the config, the dialog API on your server
   version and the token, then creates one test issue in your project.

Until the config is valid, `/bug` tells players that bug reporting is not set up, and the console lists
every problem.

## Create the BetaHub token

1. In BetaHub, open your project, then **Settings → Integrations → Auth Tokens**, and click **New Token**.
2. Name it (e.g. "Minecraft") and enable **Can Create Bug Report**.
3. **Raise "Limit Per Day" for bug reports.** BetaHub limits reports per IP address per day, 8 by default.
   Every player on your server reports through the server's IP, so with the default the *whole server* gets
   8 reports a day. Backends that share one machine or IP share the limit too. Pick a limit that matches
   how many reports you expect per day across those servers.
4. Optional: enable **Can Create Release** (see `release-label` below).
5. Copy the token (`tkn-...`) right away; BetaHub shows it only once.

The project ID is in the project URL: `https://app.betahub.io/projects/pr-XXXXXXXXXX`.

On startup and on every reload, the plugin checks the token with BetaHub (this does not use up any reports)
and warns in the console if the daily limit is still at the default or the token belongs to another project.

BetaHub hides a report it recognizes as a duplicate. The player still gets a link, which points to the hidden
issue (`.../issues/g-<id>`).

If BetaHub does not confirm a report in time, it was probably saved anyway: the player is told not to resend
yet (`messages.uncertain`) and keeps the cooldown, so they do not file a duplicate.

When the limit is used up, players see "Bug reporting is temporarily unavailable." and the console explains
what to change. BetaHub returns the same answer when the token lacks "Can Create Bug Report", so the console
message mentions both.

## Configuration

`config.yml` is commented. The main settings:

| Setting | Default | Meaning |
|---|---|---|
| `betahub.base-url` | `https://app.betahub.io` | BetaHub address |
| `betahub.project-id` | – | `pr-...` |
| `betahub.token` | – | `tkn-...` project auth token |
| `betahub.release-label` | empty | Release the reports are filed under. Empty: the Minecraft version, e.g. `1.21.11`. |
| `betahub.request-timeout-seconds` | 60 | BetaHub writes the issue title with AI while the request is open |
| `server-name` | – | Name of this backend in your network |
| `report.description-min-length` / `-max-length` | 10 / 1000 | Description length limits |
| `report.steps-max-length` | 1000 | Steps to reproduce length limit |
| `report.cooldown-seconds` | 120 | Wait time between reports per player |
| `report.attach-server-log` | false | Attach the last lines of `logs/latest.log` (IP addresses removed) |
| `report.server-log-lines` | 200 | How many lines |
| `metadata.*` | all true | Turn off coordinates, biome, client version, or game mode/health/ping |
| `custom-fields` | empty | Copy metadata into BetaHub custom fields |
| `commands.report-alias` | false | Also register `/report` (restart needed) |
| `messages.*` | | Every text players see, in [MiniMessage](https://docs.advntr.dev/minimessage/format) format |

**Release label.** If the release does not exist yet, BetaHub creates it on private projects. On public
projects it needs **Can Create Release** on the token; without it, the plugin files the report under the
latest release instead and logs a warning, so set `release-label` to an existing release or allow the token
to create releases.

**Custom fields.** Map metadata keys to the identifiers of custom fields in your project:

```yaml
custom-fields:
  server_name: server
  location: location
```

Keys: `player_name`, `player_uuid`, `server_name`, `world`, `dimension`, `coordinates`, `location`,
`tp_command`, `biome`, `minecraft_version`, `server_version`, `client_version`, `client_brand`, `game_mode`,
`ping`, `reported_at`, `plugin_version`. BetaHub creates an identifier it does not know as a hidden text field.

## Commands and permissions

| Command | Permission | Default | |
|---|---|---|---|
| `/bug` | `betahub.report` | everyone | Opens the report dialog |
| `/bugreport reload` | `betahub.admin` | op | Reloads `config.yml` |
| `/bugreport test` | `betahub.admin` | op | Checks the setup and creates one test issue (uses one report from the daily limit) |
| | `betahub.bypasscooldown` | op | No cooldown between reports |

Both admin commands also work from the server console.

## What a report contains

The player's description, then a "Game context" block:

- player name and UUID, server name, world and dimension
- block position, yaw and pitch, and a ready-to-paste teleport command, e.g.
  `/execute in minecraft:the_nether run tp @s 120.50 64.00 -35.25 90.0 10.5`
- biome, game mode, health, ping
- client version (with ViaVersion on the backend) and client brand (vanilla, fabric, ...)
- server software and version, time (UTC), plugin version

Steps to reproduce go into BetaHub's "steps" field, which BetaHub splits into steps with AI. BetaHub also writes
the issue title. The position is taken when the player runs `/bug`, so it shows where the bug was noticed.

**Privacy.** The token is never logged by the plugin. When server logs are attached, IPv4 and IPv6 addresses
and the token are removed before sending. Player names and UUIDs are sent to BetaHub.

**ViaVersion behind a proxy.** If ViaVersion runs only on the proxy, the backend cannot see the real client
version and the report shows only the client brand. Install ViaVersion on the backend too if you need it.

## Build from source

```bash
./gradlew build
```

The plugin jar is `paper/build/libs/BetaHubBugReporter-<version>.jar`. Build needs JDK 21; Gradle downloads it
(and JDK 25 for the 26.x test server) when missing.

Local test servers: `./gradlew :paper:runServer` (Paper 1.21.11) and `./gradlew :paper:runServer26` (Paper 26.3).
See [TESTING.md](TESTING.md).

## Project layout

```
core/    BetaHub client, report formatting, validation, cooldowns, log redaction, config parsing.
         No Bukkit types, so it is unit tested without a server and can serve a future Bedrock frontend.
paper/   Plugin main class, commands, context collection, the dialog (ReportDialog: all dialog API code),
         ViaVersion lookup.
scripts/ smoke-test.sh (real Paper server + fake BetaHub), stub_betahub.py
e2e/     Mineflayer bot that checks the dialog a real 1.21.11 server sends
```

## Notes on the BetaHub API

Verified against the BetaHub backend source, where it differs from the original brief:

- Issues are sent as JSON (`{"issue": {...}}`) to `POST /projects/{id}/issues.json`; BetaHub accepts JSON
  and form fields there equally.
- A used-up daily limit and a token without "Can Create Bug Report" both return
  `403 {"error":"Not allowed to report a bug."}`.
- An unknown `release_label` on a public project is refused with 403 unless the token can create releases
  (it is not silently replaced); the plugin then retries without the label.
- `BetaHub-Project-ID` is sent as documented, but BetaHub takes the project from the URL.
- The token is checked with `POST /auth/verify` (`Authorization: Bearer tkn-...`), which uses no quota.
- BetaHub generates the title synchronously with AI, so the request timeout is 60 s, not 15 s.
- One retry only when nothing can have been created (connection failed, HTTP 503). A timeout, 500, 502 or 504
  may mean the issue exists already, so it is not retried, and the player is told the report may have arrived
  instead of "try again" (their cooldown stays), to avoid duplicates.

## Open questions (TODO)

Not decided yet, so not built:

1. **Screenshots.** A server plugin cannot read the player's screen or client files. Options: players add
   them on the issue page later, BetaHub adds a player-facing "add attachments" link for token reports, or
   players use the project's Discord. The success message is configurable (`messages.submitted`) for whichever is chosen.
2. **Player identity.** BetaHub's reporter identity accepts an email or Discord ID, which Minecraft does not
   have. Reports carry the player name and UUID in the description (and optionally a custom field).
   A dedicated field or reporter mapping is open.
3. **Distribution.** Modrinth and Hangar under the BetaHub name, or private delivery to the first customer.

## License

[MIT](LICENSE)
