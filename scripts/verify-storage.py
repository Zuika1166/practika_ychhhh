#!/usr/bin/env python3
import json
import os
import subprocess
import urllib.request
from datetime import datetime, timezone

base = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")


def run(*args, capture=False):
    return subprocess.run(args, check=True, text=True, capture_output=capture)


def get(path):
    with urllib.request.urlopen(base + path, timeout=15) as response:
        return json.load(response)


category = get("/api/categories")[0]["id"]
req = urllib.request.Request(base + "/api/tickets", method="POST",
    headers={"Content-Type": "application/json"},
    data=json.dumps({"title": "Проверка сохранности", "categoryId": category, "priority": "LOW"}).encode())
with urllib.request.urlopen(req, timeout=15) as response:
    ticket = json.load(response)
ticket_id = ticket["id"]
before = get(f"/api/tickets/{ticket_id}")
history = get(f"/api/tickets/{ticket_id}/history")
run("docker", "compose", "down")
run("docker", "compose", "up", "-d", "--no-build", "--wait", "--wait-timeout", "180")
assert get(f"/api/tickets/{ticket_id}") == before
assert get(f"/api/tickets/{ticket_id}/history") == history
backup = run("bash", "scripts/backup.sh", capture=True).stdout.strip()
target = "restore_" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
run("bash", "scripts/restore.sh", backup, target)
query = f"SELECT row_to_json(t) FROM tickets t WHERE id={ticket_id}"
command = 'exec psql -U "$POSTGRES_USER" -d "$1" -At -v ON_ERROR_STOP=1 -c "$2"'
restored = json.loads(run("docker", "compose", "exec", "-T", "db", "sh", "-c", command, "sh", target, query, capture=True).stdout)
assert restored["id"] == ticket_id
assert restored["title"] == ticket["title"]
assert restored["status"] == "NEW"
for table in ("tickets", "status_history", "categories"):
    dump_query = f"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id), '[]'::jsonb) FROM {table} t"
    source = run("docker", "compose", "exec", "-T", "db", "sh", "-c", 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "$1"', "sh", dump_query, capture=True).stdout
    recovered = run("docker", "compose", "exec", "-T", "db", "sh", "-c", command, "sh", target, dump_query, capture=True).stdout
    assert json.loads(source) == json.loads(recovered), table
print(json.dumps({"result": "PASS", "ticketId": ticket_id, "backup": backup, "restoredDatabase": target, "restartPreservedData": True, "allTablesMatch": True}, ensure_ascii=False, indent=2))
