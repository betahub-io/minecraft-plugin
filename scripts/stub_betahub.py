#!/usr/bin/env python3
"""Fake BetaHub API for the smoke test. Records every request as a JSON line.

Usage: stub_betahub.py --project pr-123 --port-file PATH --log PATH
"""
import argparse
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ISSUES_PATH = re.compile(r"^/projects/(pr-[0-9A-Za-z]+)/issues\.json$")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True)
    parser.add_argument("--port-file", required=True)
    parser.add_argument("--log", required=True)
    args = parser.parse_args()
    counter = {"issues": 0}

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length") or 0)
            body = self.rfile.read(length).decode("utf-8") if length else ""
            with open(args.log, "a", encoding="utf-8") as log:
                log.write(json.dumps({
                    "path": self.path,
                    "authorization_scheme": (self.headers.get("Authorization") or "").split(" ")[0],
                    "project_header": self.headers.get("BetaHub-Project-ID"),
                    "content_type": self.headers.get("Content-Type"),
                    "user_agent": self.headers.get("User-Agent"),
                    "body": json.loads(body) if body.startswith("{") else body,
                }) + "\n")
            if self.path == "/auth/verify":
                self.reply(200, {
                    "valid": True, "token_type": "project_auth_token",
                    "project": {"id": 1, "name": "Smoke", "slug": args.project},
                    "permissions": {"can_create_bug_report": True, "can_create_feature_request": False,
                                    "can_create_ticket": False, "can_read_release_list": False,
                                    "can_create_release": True},
                    "rate_limits": {"bug_reports_per_day": 1000, "feature_requests_per_day": 8, "tickets_per_day": 8},
                    "token_name": "Smoke test",
                })
                return
            match = ISSUES_PATH.match(self.path)
            if match and match.group(1) == args.project:
                counter["issues"] += 1
                number = counter["issues"]
                self.reply(201, {"id": 1000 + number, "scoped_id": number,
                                 "url": f"https://app.betahub.io/projects/{args.project}/issues/{number}"})
                return
            self.reply(404, {"error": "Not found"})

        def reply(self, status, payload):
            data = json.dumps(payload).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    with open(args.port_file, "w", encoding="utf-8") as port_file:
        port_file.write(str(server.server_address[1]))
    server.serve_forever()


if __name__ == "__main__":
    main()
