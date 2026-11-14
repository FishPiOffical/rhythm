#!/usr/bin/env python3
"""Apply new export/packet DDL to a disposable MySQL, never to a configured application database."""
import json
import os
from pathlib import Path
import subprocess
import time
import uuid


def run(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=20, **kwargs)


name = 'rhythm-v3-feature-schema-' + uuid.uuid4().hex[:12]
container = None
try:
    image = run(['docker', 'image', 'inspect', os.environ.get('API_V3_MYSQL_IMAGE', 'mysql:8.4'), '--format', '{{.Id}}']).stdout.strip()
    container = run(['docker', 'run', '-d', '--rm', '--pull', 'never', '--network', 'none', '--memory', '768m',
                     '--name', name, '--label', 'rhythm.api-v3-feature-schema=true', '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes',
                     image, '--skip-log-bin', '--performance-schema=OFF', '--innodb-buffer-pool-size=32M']).stdout.strip()
    deadline = time.monotonic() + 65
    while True:
        ping = subprocess.run(['docker', 'exec', container, 'mysqladmin', '-uroot', 'ping'], capture_output=True, text=True, timeout=5)
        process = subprocess.run(['docker', 'exec', container, 'cat', '/proc/1/comm'], capture_output=True, text=True, timeout=5)
        if ping.returncode == 0 and process.stdout.strip() == 'mysqld':
            break
        if time.monotonic() > deadline:
            raise RuntimeError('Disposable schema MySQL startup timeout')
        time.sleep(0.3)

    def sql(statement):
        return run(['docker', 'exec', '-i', container, 'mysql', '-uroot', '-N', '-B'], input=statement).stdout.strip()

    sql('CREATE DATABASE rhythm_v3_features;')
    header = "USE rhythm_v3_features; SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';\n"
    for migration in ['sql/20261024_api_v3_post_export.sql', 'sql/20261024_api_v3_chat_red_packet.sql']:
        source = Path(migration).read_text()
        sql(header + source)
        sql(header + source)
    sql(header + Path('sql/20261024_api_v3_chat_red_packet_risk.sql').read_text())
    assert sql(header + "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_chat_red_packet' AND COLUMN_NAME='type';") == '17'
    data = json.loads(Path('src/main/resources/repository.json').read_text())['repositories']
    tables = ['api_v3_post_export', 'api_v3_post_export_chunk', 'chat_red_packet', 'chat_red_packet_slot']
    for table in tables:
        repository = next(r for r in data if r['name'] == table)
        actual = dict(line.split('\t') for line in sql(header + "SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA='rhythm_v3_features' AND TABLE_NAME='symphony_" + table + "' ORDER BY ORDINAL_POSITION;").splitlines())
        assert set(actual) == {k['name'] for k in repository['keys']}, table
        for key in repository['keys']:
            expected = {'long': 'bigint', 'int': 'int'}.get(key['type'])
            if expected:
                assert actual[key['name']] == expected, (table, key, actual)
            else:
                assert key['type'] == 'String' and actual[key['name']] in ('varchar', 'mediumtext', 'text', 'longtext'), (table, key)
        assert not table.startswith('symphony_')
    assert sql(header + 'SELECT COUNT(*) FROM information_schema.statistics WHERE TABLE_SCHEMA=DATABASE() AND NON_UNIQUE=0 AND INDEX_NAME IN (\'uq_v3_post_export_chunk\',\'uk_chat_packet_ordinal\');') == '4'
    print('PASS schema 1 real MySQL ' + sql('SELECT VERSION();') + ': base migrations apply twice and risk ALTER once, four tables match repository field/type metadata, RPS type17 and unique slot/chunk indexes exist', flush=True)
    sql(header + "START TRANSACTION; INSERT INTO symphony_chat_red_packet_slot (oId,messageId,ordinal,amount,recipientId,claimUserId,claimedAt,transferId) VALUES(REPEAT('a',64),'1',0,0,'','',0,''); INSERT INTO symphony_api_v3_post_export_chunk VALUES(REPEAT('b',64),'1','2',0,1,'YQ=='); ROLLBACK;")
    assert sql(header + 'SELECT (SELECT COUNT(*) FROM symphony_chat_red_packet_slot)+(SELECT COUNT(*) FROM symphony_api_v3_post_export_chunk);') == '0'
    print('PASS schema 2 original ordinal/data column SQL accepts bound-shape samples and InnoDB rollback restores both private tables; no Java/HTTP integration claim', flush=True)
finally:
    if container:
        run(['docker', 'rm', '-f', container])
        assert not run(['docker', 'ps', '-aq', '--filter', 'name=^/' + name + '$']).stdout.strip()
        print('Disposable schema MySQL removed; no port was published', flush=True)
