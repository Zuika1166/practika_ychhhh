#!/usr/bin/env python3
import json
import os
import urllib.request
from datetime import datetime, timedelta, timezone

base = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")
now = datetime.now(timezone.utc)


def send(method, path, data):
    request = urllib.request.Request(base + path, method=method,
        headers={"Content-Type": "application/json"}, data=json.dumps(data).encode())
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.load(response)


cases = [
    ("Принтер в кабинете 104", 1, "HIGH", -1, []),
    ("Установка офисного пакета", 2, "MEDIUM", 2, ["IN_PROGRESS"]),
    ("Настройка сети", 3, "HIGH", 1, ["IN_PROGRESS", "RESOLVED"]),
    ("Дублирующее обращение", 1, "LOW", None, ["CANCELLED"]),
    ("Проверка рабочего места", 1, "LOW", None, []),
]
for title, category, priority, days, statuses in cases:
    due = (now + timedelta(days=days)).isoformat() if days is not None else None
    ticket = send("POST", "/api/tickets", {"title": title, "description": "Демонстрационная заявка",
        "categoryId": category, "priority": priority, "dueAt": due})
    for status in statuses:
        ticket = send("PATCH", f'/api/tickets/{ticket["id"]}/status', {"status": status})
    print(json.dumps({"id": ticket["id"], "title": title, "status": ticket["status"]}, ensure_ascii=False))
