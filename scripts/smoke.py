#!/usr/bin/env python3
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

base = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")


def request(method, path, data=None, expected=200):
    payload = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(base + path, data=payload, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            code, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, body = error.code, error.read()
    if code != expected:
        raise AssertionError(f"{method} {path}: {code}, ожидался {expected}; {body.decode()}")
    return json.loads(body)


now = datetime.now(timezone.utc)
start = (now - timedelta(minutes=1)).isoformat()
end = (now + timedelta(minutes=10)).isoformat()
assert request("GET", "/actuator/health/readiness")["status"] == "UP"
category = request("GET", "/api/categories")[0]["id"]
ticket = request("POST", "/api/tickets", {
    "title": "Проверка развёртывания", "description": "Проверка жизненного цикла",
    "categoryId": category, "priority": "MEDIUM", "dueAt": end
}, 201)
path = f'/api/tickets/{ticket["id"]}'
assert ticket["status"] == "NEW"
request("PATCH", path + "/status", {"status": "IN_PROGRESS"})
request("PATCH", path + "/status", {"status": "RESOLVED"})
before = request("GET", path)
history = request("GET", path + "/history")
assert len(history) == 3
assert before["closedAt"] is not None
request("PATCH", path + "/status", {"status": "IN_PROGRESS"}, 409)
request("PUT", path, {"description": "Запрещено", "priority": "HIGH", "dueAt": None}, 409)
assert request("GET", path) == before
assert request("GET", path + "/history") == history
report = request("GET", f"/api/reports/summary?from={start.replace('+', '%2B')}&to={end.replace('+', '%2B')}")
assert report["createdCount"] >= 1
assert report["byStatus"]["RESOLVED"] >= 1
assert report["averageResolutionSeconds"] is not None
print(json.dumps({"result": "PASS", "ticketId": ticket["id"], "report": report}, ensure_ascii=False, indent=2))
