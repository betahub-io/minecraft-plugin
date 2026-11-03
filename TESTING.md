# Testing

## Automated

### Unit and integration tests: `./gradlew build`

| Module | Suite | Covers |
|---|---|---|
| core | `ReportFormatterTest`, `MetadataFieldTest` | Description and "Game context" text, teleport command, block rounding of negative coordinates, omitted parts |
| core | `ReportValidatorTest` | Min/max lengths after trimming, blank input, line endings |
| core | `CooldownTrackerTest` | Countdown, expiry, clear, a failed older report does not lift a newer cooldown, changing the cooldown on reload |
| core | `LogRedactorTest`, `LogTailTest` | IPv4/IPv6 removed (with ports, brackets, zones, embedded IPv4, `ip:` prefix, `address:port` without brackets, sentence end); times, versions, MAC addresses, `Class::method` kept; token removed; a 40,000-character colon line finishes in under 2 s; tail of large files across read chunks, UTF-8, byte cap starting at a line boundary |
| core | `ConfigParserTest` | Valid config, shipped placeholders count as missing, token/project ID format, all errors reported at once, `custom-fields`/`messages` of the wrong type, token never echoed |
| core | `IssueAssemblerTest`, `DiagnosticsTest` | Release label default, custom field mapping, admin-facing explanations |
| core | `BetaHubClientTest` (HTTP stub, `com.sun.net.httpserver`) | Path, method, `Authorization`, `BetaHub-Project-ID`, JSON body with newlines, quotes, Polish letters and emoji; optional fields omitted; 201 (incl. `scoped_id: null`, warnings); 403 daily limit; 403 plan limit; 429; 403 release permission → retry without label; 422; 401; 403 wrong project; 503 → one retry; 503 twice → failure; 500 and 502 not retried; timeout not retried; "may have been created" set for timeout, 5xx other than 503 and unreadable success, not for connection refused; unsafe issue URL refused; `/auth/verify` |
| core | `LiveBetaHubTest` | Real BetaHub: token check, a full plugin report (context block, steps, release label, custom fields, redacted log) and a plain issue with Unicode. **Runs only** when `BETAHUB_TEST_PROJECT` and `BETAHUB_TEST_TOKEN` are set (optional `BETAHUB_TEST_BASE_URL`); creates two real issues. |
| paper | `ReportFlowTest` (MockBukkit) | `/bug` → dialog with configured limits; context taken at `/bug`, not at submit; validation reopens the dialog with an error and keeps the text; async send; link in chat; cooldown, bypass permission, cooldown lifted after a failure where nothing was sent but kept after rate limit or an unconfirmed (possibly saved) report; unexpected error still answers the player; 422 message; log excerpt redacted; missing log file; custom fields; player leaving before the answer; misconfigured plugin |
| paper | `PluginTest` (MockBukkit) | Plugin loads with shipped config and lists what is missing; `/report` alias off by default; console cannot use `/bug`; non-admins do not see `/bugreport`; `/bugreport reload`; `/bugreport test` against the HTTP stub (verify → test issue), and stops before creating an issue when the token belongs to another project |
| paper | `ReportDialogTest` | Dialog response → draft with a fake `DialogResponseView` (MockBukkit cannot build dialogs); response from a non-main thread is moved to the main thread; non-player audience ignored |
| paper | `MessagesTest`, `ContextCollectorTest` | Clickable issue link, placeholders not parsed as MiniMessage, cooldown rounding, broken `messages` section falls back to defaults; metadata toggles, client version and brand |

### Real server smoke test: `scripts/smoke-test.sh <version> [--with-bot]`

Starts a real Paper server through the run-paper Gradle plugin, with the plugin pointed at a fake BetaHub
(`scripts/stub_betahub.py`), runs `bug` and `bugreport test` from the console, stops the server and checks
the log and the recorded HTTP requests.

`--with-bot` (1.21.11 only, run `npm install` in `e2e/` first) also lets a Mineflayer bot join, run `/bug`
and check the dialog packet: confirmation type, title, `description` and `steps` inputs, max length 1000,
multiline, Submit bound to a Paper click callback, Cancel without action.

Results on 2026-11-03 (macOS, arm64):

| Server | Java | Fake BetaHub | Local BetaHub backend |
|---|---|---|---|
| Paper 1.21.11-132 | 21.0.9 | pass, including `--with-bot` | pass, including `--with-bot` (issue #4 created) |
| Paper 26.3-238 | 25.0.4.1 (Temurin, downloaded by Gradle) | pass | pass (bot not run: Mineflayer supports up to 26.1) |

On the local backend, BetaHub hid the second, near-identical test report as a duplicate. It got no issue number,
so the plugin reported it as `g-<id>` (`Bug report g-8 created: .../issues/g-8`), as designed.

Checked on both: plugin enables; token verified at startup; `Dialog API OK on Paper <version>`; test issue
created; no errors, warnings or deprecation notes from the plugin in the server log; requests use
`Bearer` for `/auth/verify` and `FormUser` plus `BetaHub-Project-ID` and JSON for the issue.

### Against the real BetaHub backend

On 2026-11-03 against a local development instance of the BetaHub backend (`http://localhost:3148`,
backend branch with no changes to issue submission), with a project auth token that may create bug
reports but not releases:

- `LiveBetaHubTest`: 3 of 3 passed. Creating an issue took 8 to 13 seconds, because BetaHub writes the
  title with AI during the request.
- Stored result of the full plugin report, read back from the database: AI title "Chest in the Nether
  Disappears Upon Relogging"; release `1.21.11` created; steps split into 3; custom fields `mc_server` and
  `mc_location` created and filled; log attached with the IP replaced by `[IP]`; teleport command in the
  description.
- `smoke-test.sh` with `BETAHUB_TEST_*` pointing at that backend: see the smoke test table above.

Production (`app.betahub.io`) was not used.

### Not covered automatically

- **Submitting the dialog from a real client.** The bot cannot answer the dialog: with minecraft-data 3.119.0,
  Paper 1.21.11 decodes the bot's `custom_click_action` packet as `client_command` and disconnects it
  (library issue). The submit path is covered by `ReportFlowTest` and `ReportDialogTest`, and the HTTP part
  by `/bugreport test` on real servers.
- **Production BetaHub** (`app.betahub.io`): only a local instance of the same backend was used. Error paths
  (daily limit, release permission on a public project, 5xx) were tested against the stub only.
- **Look and feel of the dialog** in a real client: see below.

## Manual test with a Minecraft client

Needs a Java Edition client matching the server version, a BetaHub test project and a token with
"Can Create Bug Report".

1. Configure `plugins/BetaHubBugReporter/config.yml` (project ID, token, `server-name: manual-test`), run
   `/bugreport reload` and `/bugreport test` in the console. Expect "Config OK.", "Dialog API OK on Paper
   ...", the token line, and "Bug report #N created: ...". Open the link; the issue exists.
2. Join as a non-op player. Stand somewhere recognizable (e.g. in the Nether), note the F3 coordinates.
3. Run `/bug`. A dialog titled "Report a bug" opens with the explanation text, a large "What happened?"
   field, a smaller "Steps to reproduce (optional)" field, and Submit / Cancel buttons.
   - Both fields accept several lines and scroll; you cannot type more than 1000 characters.
4. Type `short`, press Submit. The dialog reopens with a red line "Please describe the bug in at least 10
   characters." and your text still in the field.
5. Enter a real description with a line break and Polish characters (e.g. "Skrzynia znika po relogu"),
   steps on two lines, press Submit. The action bar shows "Sending report...", then chat says
   "Thanks! Bug report submitted. View it on BetaHub".
6. Click "View it on BetaHub". The issue opens. Check: your text first, then "Game context" with your name,
   UUID, `manual-test`, world and dimension, the coordinates from step 2, biome, game mode, client brand,
   and a teleport command. Paste the teleport command in game as an op: you land on the same spot.
   Steps appear as separate steps.
7. Run `/bug` again right away: "Please wait ...s before sending another report."
8. Run `/bug`, press Cancel, then Escape on a second try: the dialog closes, nothing is sent, no message.
9. Run `/bug`, wait more than 1 hour, then press Submit: no report is sent (the button expires after 1 hour).
   Note what the client shows; run `/bug` again to report.
10. As op, `/bugreport reload` after changing `messages.dialog-title`: the next `/bug` shows the new title.
11. Optional: set the token's "Limit Per Day" to 1 in BetaHub, send two reports. The second shows "Bug
    reporting is temporarily unavailable." and the console explains the limit.
12. Optional, network: on a Velocity network, repeat step 6 on two backends with different `server-name`
    values and with ViaVersion on the backend; the report shows the right server and the client version.
