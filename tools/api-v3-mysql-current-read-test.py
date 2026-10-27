#!/usr/bin/env python3
"""Check repeater locking-count semantics in an isolated, disposable MySQL container.

Uses a locally cached image, no network, no published ports and no business database.
This checks InnoDB SQL semantics, not the HTTP service or complete Java transaction.
"""
import os
from pathlib import Path
import selectors
import subprocess
import time
import uuid


def run(args, **kwargs):
    return subprocess.run(args, check=True, text=True, capture_output=True, timeout=20, **kwargs)


container = None
clients = []


def sql(statement):
    return run(["docker", "exec", container, "mysql", "-uroot", "-N", "-B", "-e", statement]).stdout.strip()


def session():
    client = subprocess.Popen(
        ["docker", "exec", "-i", container, "mysql", "-uroot", "-N", "-B", "--unbuffered", "rhythm_v3_check"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=0)
    clients.append(client)
    return client


def send(client, statement):
    client.stdin.write((statement + "\n").encode())
    client.stdin.flush()


def until(client, marker):
    data = b""
    deadline = time.monotonic() + 15
    with selectors.DefaultSelector() as selector:
        selector.register(client.stdout, selectors.EVENT_READ)
        while time.monotonic() < deadline:
            if not selector.select(0.2):
                continue
            chunk = os.read(client.stdout.fileno(), 65536)
            if not chunk:
                detail = client.stderr.read().decode(errors="replace")
                raise RuntimeError("MySQL client closed before " + marker + ": " + detail)
            data += chunk
            if marker.encode() in data.splitlines():
                return data.decode()
    raise RuntimeError("MySQL verification timed out waiting for " + marker)


try:
    source = Path("src/main/java/org/b3log/symphony/service/RepeaterLikeMutationService.java").read_text()
    assert 'WHERE repeaterLikeContentId=? FOR UPDATE",itemId)' in source, "Review the production count SQL"
    image = os.environ.get("API_V3_MYSQL_IMAGE", "mysql:8.4")
    image_id = run(["docker", "image", "inspect", image, "--format", "{{.Id}}"]).stdout.strip()
    container = run([
        "docker", "run", "-d", "--rm", "--pull", "never", "--network", "none", "--memory", "768m",
        "--label", "rhythm.api-v3-verification=true", "--name", "rhythm-v3-mysql-check-" + uuid.uuid4().hex[:10],
        "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", image_id,
        "--skip-log-bin", "--performance-schema=OFF", "--innodb-buffer-pool-size=32M"
    ]).stdout.strip()
    deadline = time.monotonic() + 65
    while True:
        ready = subprocess.run(["docker", "exec", container, "mysqladmin", "-uroot", "ping"],
                               text=True, capture_output=True, timeout=5)
        # The entrypoint briefly starts a temporary bootstrap server before execing mysqld.
        # Testing that server races its intentional shutdown and can mimic a connection fault.
        process = subprocess.run(["docker", "exec", container, "cat", "/proc/1/comm"],
                                 text=True, capture_output=True, timeout=5)
        if ready.returncode == 0 and process.stdout.strip() == "mysqld":
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Disposable MySQL did not become ready")
        time.sleep(0.3)
    print("Server:", sql("SELECT VERSION()"), flush=True)
    sql("CREATE DATABASE rhythm_v3_check; COMMIT; USE rhythm_v3_check; "
        "CREATE TABLE contents(oId VARCHAR(19) PRIMARY KEY, repeaterContentLikeCount INT NOT NULL) ENGINE=InnoDB; "
        "CREATE TABLE likes(oId VARCHAR(19) PRIMARY KEY,repeaterLikeContentId VARCHAR(19) NOT NULL,"
        "repeaterLikeUserId VARCHAR(19) NOT NULL, INDEX target_idx(repeaterLikeContentId)) ENGINE=InnoDB; COMMIT; "
        "START TRANSACTION; INSERT INTO contents VALUES('1',1); INSERT INTO likes VALUES('1','1','1'); COMMIT;")
    a = session()
    send(a, "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION; "
            "SELECT CONCAT('SNAPSHOT_READY:',COUNT(*)) FROM likes WHERE repeaterLikeContentId='1';")
    assert "SNAPSHOT_READY:1" in until(a, "SNAPSHOT_READY:1")
    sql("USE rhythm_v3_check; START TRANSACTION; SELECT oId FROM contents WHERE oId='1' FOR UPDATE; "
        "INSERT INTO likes VALUES('2','1','2'); UPDATE contents SET repeaterContentLikeCount=2 WHERE oId='1'; COMMIT;")
    send(a, "SELECT oId FROM contents WHERE oId='1' FOR UPDATE; "
            "SELECT CONCAT('OLD:',COUNT(*)) FROM likes WHERE repeaterLikeContentId='1'; "
            "SELECT CONCAT('CURRENT:',COUNT(*)) FROM likes WHERE repeaterLikeContentId='1' FOR UPDATE; "
            "UPDATE contents SET repeaterContentLikeCount=2 WHERE oId='1'; COMMIT; SELECT 'COUNT_DONE';")
    observed = until(a, "COUNT_DONE")
    assert "OLD:1" in observed and "CURRENT:2" in observed, observed
    assert sql("SELECT repeaterContentLikeCount FROM rhythm_v3_check.contents WHERE oId='1'") == "2"
    print("PASS 1 locking aggregate sees committed likes beyond an older RR snapshot", flush=True)

    send(a, "START TRANSACTION; SELECT oId FROM contents WHERE oId='1' FOR UPDATE; "
            "DELETE FROM likes WHERE repeaterLikeUserId='1'; "
            "SELECT CONCAT('AFTER_DELETE:',COUNT(*)) FROM likes WHERE repeaterLikeContentId='1' FOR UPDATE; "
            "UPDATE contents SET repeaterContentLikeCount=1 WHERE oId='1'; ROLLBACK; SELECT 'ROLLBACK_DONE';")
    assert "AFTER_DELETE:1" in until(a, "ROLLBACK_DONE")
    assert sql("SELECT COUNT(*) FROM rhythm_v3_check.likes") == "2"
    assert sql("SELECT repeaterContentLikeCount FROM rhythm_v3_check.contents") == "2"
    print("PASS 2 own deletion is included and rollback restores likes plus counter", flush=True)

    send(a, "START TRANSACTION; SELECT oId FROM contents WHERE oId='1' FOR UPDATE; SELECT 'TARGET_HELD';")
    until(a, "TARGET_HELD")
    b = session()
    send(b, "SET SESSION innodb_lock_wait_timeout=10; START TRANSACTION; "
            "SELECT oId FROM contents WHERE oId='1' FOR UPDATE; "
            "INSERT INTO likes VALUES('4','1','4'); "
            "SELECT CONCAT('B_COUNT:',COUNT(*)) FROM likes WHERE repeaterLikeContentId='1' FOR UPDATE; "
            "UPDATE contents SET repeaterContentLikeCount=4 WHERE oId='1'; COMMIT; SELECT 'B_DONE';")
    with selectors.DefaultSelector() as selector:
        selector.register(b.stdout, selectors.EVENT_READ)
        assert not selector.select(0.25), "Second writer did not wait for the shared target lock"
    send(a, "INSERT INTO likes VALUES('3','1','3'); "
            "UPDATE contents SET repeaterContentLikeCount=3 WHERE oId='1'; COMMIT; SELECT 'A_DONE';")
    until(a, "A_DONE")
    assert "B_COUNT:4" in until(b, "B_DONE")
    assert sql("SELECT COUNT(*) FROM rhythm_v3_check.likes") == "4"
    assert sql("SELECT repeaterContentLikeCount FROM rhythm_v3_check.contents") == "4"
    print("PASS 3 target lock serializes two writers and committed counter matches rows", flush=True)
finally:
    for client in clients:
        if client.poll() is None:
            client.terminate()
        try:
            client.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            client.kill()
            client.communicate()
    if container:
        removed = subprocess.run(["docker", "rm", "-f", container], text=True, capture_output=True, timeout=20)
        if removed.returncode:
            raise RuntimeError("Could not remove the disposable verification container: " + container)
        print("Disposable MySQL container removed", flush=True)
