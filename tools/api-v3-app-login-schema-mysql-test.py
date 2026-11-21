#!/usr/bin/env python3
"""Production QR DDL + InnoDB primitives on a disposable offline MySQL; not full Java/HTTP concurrency."""
import json
import os
from pathlib import Path
import select
import subprocess
import time
import uuid

def run(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=25, **kwargs)

container = None
name = 'rhythm-v3-app-login-' + uuid.uuid4().hex[:12]
try:
    image = run(['docker', 'image', 'inspect', os.environ.get('API_V3_MYSQL_IMAGE', 'mysql:8.4'), '--format', '{{.Id}}']).stdout.strip()
    container = run(['docker', 'run', '-d', '--rm', '--pull', 'never', '--network', 'none', '--memory', '768m', '--name', name,
                     '--label', 'rhythm.v3-app-login-test=true', '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', image,
                     '--skip-log-bin', '--innodb-buffer-pool-size=32M']).stdout.strip()
    deadline = time.monotonic() + 65
    while True:
        ping = subprocess.run(['docker', 'exec', container, 'mysqladmin', '-uroot', 'ping'], capture_output=True, text=True, timeout=5)
        process = subprocess.run(['docker', 'exec', container, 'cat', '/proc/1/comm'], capture_output=True, text=True, timeout=5)
        if ping.returncode == 0 and process.stdout.strip() == 'mysqld':
            break
        if time.monotonic() > deadline:
            raise RuntimeError('Isolated MySQL startup timeout')
        time.sleep(.2)
    cli = ['docker', 'exec', '-i', container, 'mysql', '-uroot', '-N', '-B', '--unbuffered']
    def sql(statement):
        return run(cli, input=statement).stdout.strip()
    sql('CREATE DATABASE app_login_fixture CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;')
    prefix = 'USE app_login_fixture; SET SESSION sql_mode=\'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION\';\n'
    sql(prefix + 'CREATE TABLE symphony_api_v3_client(oId VARCHAR(19) PRIMARY KEY,clientId VARCHAR(64) UNIQUE,name VARCHAR(128),scope VARCHAR(255),status INT) ENGINE=InnoDB;')
    migration = Path('sql/20261025_api_v3_app_login.sql').read_text()
    sql(prefix + migration)
    sql(prefix + "UPDATE symphony_api_v3_client SET status=1,scope='profile.read' WHERE clientId='fishpi_app_v3';")
    sql(prefix + migration)
    assert sql(prefix + "SELECT CONCAT(status,':',scope) FROM symphony_api_v3_client WHERE clientId='fishpi_app_v3';") == '1:profile.read'
    actual = dict(line.split('\t') for line in sql(prefix + "SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_api_v3_app_login_request';").splitlines())
    repository = next(r for r in json.loads(Path('src/main/resources/repository.json').read_text())['repositories'] if r['name'] == 'api_v3_app_login_request')
    assert set(actual) == {k['name'] for k in repository['keys']}
    for key in repository['keys']:
        assert actual[key['name']] == {'String': 'varchar', 'long': 'bigint'}[key['type']]
    assert sql(prefix + "SELECT COUNT(*) FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_api_v3_app_login_request' AND INDEX_NAME IN ('idx_v3_app_login_expiry','idx_v3_app_login_owner');") == '3'
    print('PASS app login mysql 1 real production migration repeats, native registration does not overwrite disabled/custom scope, repository columns/types and expiry/owner indexes match', flush=True)
    table = 'symphony_api_v3_app_login_request'
    request = 'A' * 43
    values = f"('{request}','1','100',0,'profile.read','WEB_APPROVED','{'B'*43}',1700000000000,1700000120000,1700000000000)"
    sql(prefix + f'INSERT INTO {table} VALUES {values}; CREATE TABLE token_sink(oId INT AUTO_INCREMENT PRIMARY KEY,kind VARCHAR(10)) ENGINE=InnoDB;')
    sql(prefix + f"START TRANSACTION; INSERT INTO token_sink(kind) VALUES('access'),('refresh'); UPDATE {table} SET state='CONSUMED' WHERE oId='{request}' AND state='WEB_APPROVED' AND codeChallenge='{'B'*43}'; ROLLBACK;")
    assert sql(prefix + f"SELECT CONCAT((SELECT COUNT(*) FROM token_sink),':',state) FROM {table} WHERE oId='{request}';") == '0:WEB_APPROVED'
    print('PASS app login mysql 2 native-pair sink and production-shaped conditional consume both roll back under real InnoDB', flush=True)
    def consume(hold=False):
        return prefix + f"""START TRANSACTION;
SELECT clientId INTO @locked_client FROM symphony_api_v3_client ORDER BY clientId FOR UPDATE;
SELECT 'LOCKED';
{('SELECT SLEEP(1.5);' if hold else '')}
UPDATE {table} SET state='CONSUMED',updatedAt=1700000000001 WHERE oId='{request}' AND state='WEB_APPROVED' AND codeChallenge='{'B'*43}';
SET @won=ROW_COUNT();
INSERT INTO token_sink(kind) SELECT 'access' WHERE @won=1;
INSERT INTO token_sink(kind) SELECT 'refresh' WHERE @won=1;
COMMIT;
SELECT CONCAT('WON:',@won);
"""
    first = subprocess.Popen(cli, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    first.stdin.write(consume(True)); first.stdin.close()
    deadline = time.monotonic() + 8
    while True:
        if time.monotonic() > deadline:
            raise RuntimeError('First lock marker timeout')
        ready, _, _ = select.select([first.stdout], [], [], .1)
        if ready and first.stdout.readline().strip() == 'LOCKED':
            break
    second = subprocess.Popen(cli, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    second.stdin.write(consume()); second.stdin.close()
    waited = False
    deadline = time.monotonic() + 1
    while time.monotonic() < deadline:
        if int(sql('SELECT COUNT(*) FROM performance_schema.data_lock_waits;')) > 0:
            waited = True
            break
        time.sleep(.03)
    assert waited, 'No actual InnoDB lock wait observed'
    first.wait(timeout=10); second.wait(timeout=10)
    assert first.returncode == second.returncode == 0
    assert 'WON:1' in first.stdout.read()
    assert 'WON:0' in second.stdout.read()
    assert sql(prefix + f"SELECT CONCAT((SELECT COUNT(*) FROM token_sink),':',state) FROM {table} WHERE oId='{request}';") == '2:CONSUMED'
    print('PASS app login mysql 3 independent SQL consumers exhibit actual InnoDB lock waiting and one conditional consume with exactly one two-row token sink; not Java service concurrency', flush=True)
finally:
    if container:
        subprocess.run(['docker', 'rm', '-f', container], capture_output=True, text=True, timeout=20)
