#!/usr/bin/env bash
# Starts a real Paper server with the plugin (via run-paper), points it at a fake BetaHub,
# runs /bug and /bugreport test from the console, stops the server and checks the log.
#
# Usage: scripts/smoke-test.sh 1.21.11|26.3 [--with-bot]
#   --with-bot  also let a Mineflayer bot join, run /bug and check the dialog it receives
#               (1.21.11 only; run `npm install` in e2e/ first)
set -euo pipefail

VERSION="${1:?usage: smoke-test.sh 1.21.11|26.3 [--with-bot]}"
WITH_BOT=0
[[ "${2:-}" == "--with-bot" ]] && WITH_BOT=1
if (( WITH_BOT )) && [[ "$VERSION" != "1.21.11" ]]; then
  echo "--with-bot needs 1.21.11: Mineflayer does not support $VERSION" >&2; exit 2
fi
case "$VERSION" in
  1.21.11) TASK=runServer ;;
  26.3) TASK=runServer26 ;;
  *) echo "unsupported version $VERSION" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$ROOT/run/$VERSION"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/betahub-smoke.XXXXXX")"
PROJECT="pr-1234567890"
START_TIMEOUT=900   # first run downloads Paper (and maybe a JDK)
COMMAND_TIMEOUT=60

progress() { echo "::progress::? $1" >&2; echo "== $1"; }

cleanup() {
  [[ -n "${STUB_PID:-}" ]] && kill "$STUB_PID" 2>/dev/null || true
  [[ -n "${SERVER_PID:-}" ]] && kill "$SERVER_PID" 2>/dev/null || true
  exec 3>&- 2>/dev/null || true
}
trap cleanup EXIT

wait_for() { # file pattern timeout
  local deadline=$((SECONDS + $3))
  until grep -qE "$2" "$1" 2>/dev/null; do
    if (( SECONDS > deadline )); then echo "timeout waiting for: $2" >&2; return 1; fi
    if [[ -n "${SERVER_PID:-}" ]] && ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "server exited before: $2" >&2; return 1
    fi
    sleep 1
  done
}

progress "Starting fake BetaHub"
python3 -I "$ROOT/scripts/stub_betahub.py" --project "$PROJECT" --port-file "$WORK/stub-port" --log "$WORK/requests.jsonl" &
STUB_PID=$!
for _ in $(seq 1 50); do [[ -s "$WORK/stub-port" ]] && break; sleep 0.1; done
STUB_PORT="$(cat "$WORK/stub-port")"
MC_PORT="$(python3 -I -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1])')"

progress "Preparing $RUN_DIR"
mkdir -p "$RUN_DIR/plugins/BetaHubBugReporter"
rm -f "$RUN_DIR/logs/latest.log"
cat > "$RUN_DIR/plugins/BetaHubBugReporter/config.yml" <<YAML
betahub:
  base-url: "http://127.0.0.1:$STUB_PORT"
  project-id: "$PROJECT"
  token: "tkn-smoketest123"
server-name: "smoke-$VERSION"
YAML
cat > "$RUN_DIR/server.properties" <<PROPS
online-mode=false
server-port=$MC_PORT
generate-structures=false
spawn-protection=0
PROPS
echo "eula=true" > "$RUN_DIR/eula.txt"

progress "Starting Paper $VERSION (task $TASK)"
mkfifo "$WORK/stdin"
"$ROOT/gradlew" -p "$ROOT" ":paper:$TASK" --console=plain < "$WORK/stdin" > "$WORK/gradle.out" 2>&1 &
SERVER_PID=$!
exec 3>"$WORK/stdin"
LOG="$RUN_DIR/logs/latest.log"
wait_for "$LOG" 'Done \([0-9.,]+s\)!' "$START_TIMEOUT"

BOT_FAIL=0
if (( WITH_BOT )); then
  progress "Bot joins and opens the dialog"
  node "$ROOT/e2e/bot.mjs" "$MC_PORT" "$VERSION" | tee "$WORK/bot.out" || BOT_FAIL=1
fi

progress "Running commands"
echo "bug" >&3
echo "bugreport test" >&3
wait_for "$LOG" 'Bug report #[0-9]+ created|BetaHub rejected|Could not send' "$COMMAND_TIMEOUT"
sleep 1
echo "stop" >&3
wait "$SERVER_PID" || true
SERVER_PID=""

progress "Checking results"
FAIL=0
check() { # description pattern
  if grep -qE "$2" "$LOG"; then echo "pass  $1"; else echo "FAIL  $1"; FAIL=1; fi
}
check_absent() { # description pattern
  if grep -E "$2" "$LOG" | grep -q .; then echo "FAIL  $1:"; grep -E "$2" "$LOG" | head -20; FAIL=1; else echo "pass  $1"; fi
}
check "plugin enabled"                       '\[BetaHubBugReporter\] Enabling BetaHubBugReporter'
check "token verified at startup"            "BetaHub token 'Smoke test' is valid for project $PROJECT"
check "console /bug refused"                 'Only players can report bugs\.'
check "dialog API works on this server"      "Dialog API OK on Paper $VERSION\."
check "test issue created"                   "Bug report #1 created: https://app.betahub.io/projects/$PROJECT/issues/1"
check_absent "no errors from the plugin"           '(ERROR|SEVERE)\]: .*[Bb]eta[Hh]ub|at io\.betahub\.'
check_absent "no warnings from the plugin"         'WARN\]: \[BetaHubBugReporter\]'
check_absent "no deprecation notes about the plugin" '[Dd]eprecat.*[Bb]eta[Hh]ub'

if (( WITH_BOT )); then
  if (( BOT_FAIL )); then echo "FAIL  bot flow"; FAIL=1; else echo "pass  bot flow"; fi
fi

python3 -I - "$WORK/requests.jsonl" "$PROJECT" <<'PY' || FAIL=1
import json, sys
requests = [json.loads(line) for line in open(sys.argv[1], encoding="utf-8")]
project = sys.argv[2]
issues = [r for r in requests if r["path"].endswith("/issues.json")]
ok = True
def expect(cond, text):
    global ok
    print(("pass  " if cond else "FAIL  ") + text)
    ok = ok and cond
expect(any(r["path"] == "/auth/verify" and r["authorization_scheme"] == "Bearer" for r in requests), "verify call uses Bearer token")
expect(len(issues) == 1, "exactly one issue request")
if issues:
    issue = issues[0]
    expect(issue["authorization_scheme"] == "FormUser", "issue call uses FormUser token")
    expect(issue["project_header"] == project, "BetaHub-Project-ID header sent")
    expect(issue["content_type"] == "application/json", "JSON body")
    expect("smoke-" in issue["body"]["issue"]["description"], "server name in description")
    expect(issue["user_agent"].startswith("BetaHubBugReporter/"), "user agent")
sys.exit(0 if ok else 1)
PY

echo
echo "Server log: $LOG"
echo "Requests:   $WORK/requests.jsonl"
if (( FAIL )); then echo "SMOKE TEST FAILED ($VERSION)"; exit 1; fi
echo "SMOKE TEST PASSED ($VERSION)"
