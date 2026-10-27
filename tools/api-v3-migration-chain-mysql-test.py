#!/usr/bin/env python3
"""Check the ordered v3 SQL chain in a disposable MySQL; never connect to application DBs.

Legacy dependency tables below are deliberately minimal fixtures, not a full old installation.
New v3 tables and ALTERs come from the unchanged migration files.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid


MIGRATIONS = [
    '20261016_api_v3.sql',
    '20261019_api_v3_oauth.sql',
    '20261019_account_registration.sql',
    '20261019_api_v3_private_messages.sql',
    '20261020_api_v3_private_message_writes.sql',
    '20261020_api_v3_private_withdrawal.sql',
    '20261020_api_v3_private_realtime.sql',
    '20261022_api_v3_membership.sql',
    '20261023_account_credential_state.sql',
    '20261023_api_v3_mfa.sql',
    '20261023_api_v3_contact_verification.sql',
    '20261023_api_v3_fish_game_submission.sql',
    '20261024_api_v3_post_export.sql',
    '20261024_api_v3_chat_red_packet.sql',
    '20261024_api_v3_chat_red_packet_risk.sql',
    '20261024_api_v3_identity_application.sql',
]


def run(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=25, **kwargs)


name = 'rhythm-v3-chain-' + uuid.uuid4().hex[:12]
container = None
try:
    image = run(['docker', 'image', 'inspect', os.environ.get('API_V3_MYSQL_IMAGE', 'mysql:8.4'), '--format', '{{.Id}}']).stdout.strip()
    container = run(['docker', 'run', '-d', '--rm', '--pull', 'never', '--network', 'none', '--memory', '768m',
                     '--name', name, '--label', 'rhythm.api-v3-migration-chain=true', '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes',
                     image, '--skip-log-bin', '--performance-schema=OFF', '--innodb-buffer-pool-size=32M']).stdout.strip()
    deadline = time.monotonic() + 75
    while True:
        ping = subprocess.run(['docker', 'exec', container, 'mysqladmin', '-uroot', 'ping'], capture_output=True, text=True, timeout=5)
        process = subprocess.run(['docker', 'exec', container, 'cat', '/proc/1/comm'], capture_output=True, text=True, timeout=5)
        if ping.returncode == 0 and process.stdout.strip() == 'mysqld':
            break
        if time.monotonic() > deadline:
            raise RuntimeError('Disposable migration MySQL startup timeout')
        time.sleep(0.3)

    def sql(statement):
        return run(['docker', 'exec', '-i', container, 'mysql', '-uroot', '-N', '-B'], input=statement).stdout.strip()

    sql('CREATE DATABASE rhythm_v3_chain CHARACTER SET utf8mb4;')
    header = "USE rhythm_v3_chain; SET SESSION sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';\n"
    # Only the legacy columns touched by these ALTERs/indexes are needed here.
    legacy = {
        'verifycode': '`oId` VARCHAR(19) PRIMARY KEY',
        'chat_info': '`oId` VARCHAR(19) PRIMARY KEY,fromId VARCHAR(19),toId VARCHAR(19),content VARCHAR(255)',
        'chat_unread': '`oId` VARCHAR(19) PRIMARY KEY,fromId VARCHAR(19),toId VARCHAR(19)',
        'chat_list': '`oId` VARCHAR(19) PRIMARY KEY,sessionId VARCHAR(64)',
        'pointtransfer_redc': '`oId` VARCHAR(19) PRIMARY KEY,rawId VARCHAR(19),userId VARCHAR(19)',
        'option': '`oId` VARCHAR(128) PRIMARY KEY,optionValue TEXT,optionCategory VARCHAR(32)',
    }
    for table, columns in legacy.items():
        sql(header + f'CREATE TABLE `symphony_{table}` ({columns}) ENGINE=InnoDB;')
    sql(header + "CREATE TABLE symphony_user(oId VARCHAR(19) PRIMARY KEY,userName VARCHAR(32),userStatus INT); INSERT INTO symphony_user VALUES('2','adlered',0),('3','Adlered',0);")
    for prerequisite in ['20260622_openid_refresh_token.sql', '20260907_fish_game.sql', '20260908_fish_game_remove_oauth.sql']:
        sql(header + Path('sql', prerequisite).read_text())
    sql(header + "INSERT INTO symphony_verifycode VALUES('1'); INSERT INTO symphony_chat_info VALUES('1','2','3',NULL); INSERT INTO symphony_chat_unread VALUES('1','2','3');")
    # Create base tables, then seed the pre-v3 website policy and a customized client.
    base = Path('sql', MIGRATIONS[0]).read_text()
    sql(header + base)
    old_scope = 'profile.read content.read activity.read content.write comments.write reactions.write follows.write'
    sql(header + f"INSERT INTO symphony_api_v3_client VALUES('1','website_v3','Website','{old_scope}',0),('2','custom','Custom','profile.read',0);")
    for migration in MIGRATIONS:
        sql(header + Path('sql', migration).read_text())
        if migration == '20261024_api_v3_chat_red_packet.sql':
            sql(header + "INSERT INTO symphony_chat_red_packet VALUES('1','2','average','',100,1,0,100,97,3,'3',1);"
                "INSERT INTO symphony_chat_red_packet_slot VALUES(REPEAT('a',64),'1',0,97,'','',0,'');")
    print(f'PASS chain 1: {len(MIGRATIONS)} exact ordered v3 migrations applied on MySQL ' + sql('SELECT VERSION();'), flush=True)
    repositories = json.loads(Path('src/main/resources/repository.json').read_text())['repositories']
    names = {r['name'] for r in repositories if r['name'].startswith('api_v3_') or r['name'] in
             ('account_credential_state', 'account_contact_code', 'chat_red_packet', 'chat_red_packet_slot')}
    created = {name for migration in MIGRATIONS for name in re.findall(r'CREATE TABLE IF NOT EXISTS `symphony_([^`]+)`', Path('sql', migration).read_text())}
    assert names <= created, names - created
    for repository in repositories:
        table = repository['name']
        if table not in names:
            continue
        actual = {line.split('\t')[0]: line.split('\t')[1:] for line in sql(header +
            "SELECT COLUMN_NAME,DATA_TYPE,COALESCE(CHARACTER_MAXIMUM_LENGTH,0) FROM information_schema.columns "
            f"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='symphony_{table}';").splitlines()}
        assert set(actual) == {key['name'] for key in repository['keys']}, (table, actual)
        for key in repository['keys']:
            column_type, column_length = actual[key['name']]
            expected = {'long': 'bigint', 'int': 'int'}.get(key['type'])
            if expected:
                assert column_type == expected, (table, key, actual[key['name']])
            else:
                assert key['type'] == 'String' and column_type in ('varchar', 'mediumtext', 'text', 'longtext'), (table, key)
                if column_type == 'varchar':
                    assert int(column_length) == key['length'], (table, key, column_length)
    print(f'PASS chain 2: all {len(names)} new v3 repository tables match exact fields, numeric types and VARCHAR lengths', flush=True)
    scope = sql(header + "SELECT scope FROM symphony_api_v3_client WHERE clientId='website_v3';")
    assert scope == old_scope + ' notifications.read notifications.write activity.write settings.write uploads.write messages.read messages.write', scope
    assert sql(header + "SELECT scope FROM symphony_api_v3_client WHERE clientId='custom';") == 'profile.read'
    assert sql(header + "SELECT CONCAT('proof:',accountContext) FROM symphony_verifycode WHERE oId=1;") == 'proof:'
    assert sql(header + "SELECT CONCAT('link:',messageId) FROM symphony_chat_unread WHERE oId=1;") == 'link:'
    assert sql(header + 'SELECT content IS NULL FROM symphony_chat_info WHERE oId=1;') == '1'
    assert sql(header + "SELECT optionCategory FROM symphony_option WHERE oId='apiV3FishGameSubmission';") == 'internal'
    assert sql(header + "SELECT CONCAT(senderGesture,':',poolPoints) FROM symphony_chat_red_packet WHERE oId='1';") == '-1:97'
    assert sql(header + "SELECT CONCAT(amount,':',gesture,':',senderRefundPoints,':',senderTransferId) FROM symphony_chat_red_packet_slot WHERE messageId='1';") == '97:-1:0:'
    print('PASS chain 3: exact default scope upgrade, custom scopes and existing legacy proof/unread/private content/safe packet records preserved', flush=True)
    assert sql(header + "SELECT userId FROM symphony_api_v3_identity_reviewer WHERE oId='enterprise';") == '2'
    sql(header + "UPDATE symphony_user SET userName='renamed-reviewer' WHERE oId='2'; INSERT INTO symphony_user VALUES('4','adlered',0);")
    identity_ddl = Path('sql/20261024_api_v3_identity_application.sql').read_text()
    sql(header + identity_ddl)
    assert sql(header + "SELECT userId FROM symphony_api_v3_identity_reviewer WHERE oId='enterprise';") == '2'
    sql(header + "INSERT INTO symphony_api_v3_identity_application VALUES('1','9','enterprise','pending','9',1,0,0,'');")
    try:
        sql(header + "INSERT INTO symphony_api_v3_identity_application VALUES('2','9','enterprise','pending','9',1,0,0,'');")
        raise AssertionError('duplicate pending owner accepted')
    except subprocess.CalledProcessError:
        pass
    sql(header + "UPDATE symphony_api_v3_identity_application SET pendingOwnerId=NULL,status='rejected' WHERE oId='1';"
        "INSERT INTO symphony_api_v3_identity_application VALUES('2','9','enterprise','pending','9',1,0,0,'');")
    sql(header + "DELETE FROM symphony_api_v3_identity_reviewer; UPDATE symphony_user SET userStatus=1 WHERE oId='4';")
    sql(header + identity_ddl)
    assert sql(header + "SELECT COUNT(*) FROM symphony_api_v3_identity_reviewer;") == '0'
    print('PASS chain 5: enterprise reviewer binds exact active account once across renames; unavailable binding fails closed; one pending owner enforced by unique key', flush=True)
    # Exercise the actual deployed-ticket upgrade branch, not only fresh CREATEs.
    sql('CREATE DATABASE rhythm_v3_old_ticket; USE rhythm_v3_old_ticket; CREATE TABLE symphony_api_v3_socket_ticket '
        '(oId VARCHAR(64) PRIMARY KEY,userId VARCHAR(19),parentTokenId VARCHAR(19),createdAt BIGINT,expiresAt BIGINT,consumedAt BIGINT); '
        "INSERT INTO symphony_api_v3_socket_ticket VALUES(REPEAT('a',64),'1','2',1,2,0);")
    sql('USE rhythm_v3_old_ticket;\n' + base)
    assert sql("USE rhythm_v3_old_ticket; SELECT CONCAT(channel,':',targetId,':',userId) FROM symphony_api_v3_socket_ticket;") == 'notifications::1'
    print('PASS chain 4: existing socket ticket gains default notification binding without changing owner', flush=True)
finally:
    if container:
        run(['docker', 'rm', '-f', container])
        assert not run(['docker', 'ps', '-aq', '--filter', 'name=^/' + name + '$']).stdout.strip()
        print('Disposable migration MySQL removed; no network or port published', flush=True)
