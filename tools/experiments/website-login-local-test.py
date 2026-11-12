#!/usr/bin/env python3
"""只对本地 Rhythm 运行；账号文件由开发者在本地测试库准备，绝不输出凭据。"""
import argparse
import hashlib
import http.cookiejar
import json
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://localhost:8081')
parser.add_argument('--accounts', required=True)
parser.add_argument('--fault-test', action='store_true', help='仅在 rhythm-dev-mysql 中对本次测试账号注入撤销故障')
args = parser.parse_args()
assert urllib.parse.urlparse(args.base).hostname in ('localhost', '127.0.0.1'), '仅允许本地环境'
accounts = json.load(open(args.accounts))
assert len(accounts) >= 2

class Browser:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def request(self, path, body=None, token=None, origin=None, cookie=None):
        # 浏览器允许 localhost 的 Secure Cookie；urllib 不支持此例外，仅本地夹具模拟传输。
        for item in self.jar:
            item.secure = False
        headers = {'Origin': origin or args.base, 'User-Agent': 'Mozilla/5.0 local-SSO-test'}
        if body is not None:
            headers['Content-Type'] = 'application/json'
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if cookie:
            headers['Cookie'] = cookie
        request = urllib.request.Request(args.base + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        try:
            response = self.opener.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        raw = response.read()
        try:
            data = json.loads(raw)
        except ValueError:
            data = None
        return response.status, data, response

    def login(self, account, legacy=False, remember=True):
        payload = {'nameOrEmail': account['name'], 'userPassword': hashlib.md5(account['password'].encode()).hexdigest()}
        if legacy:
            payload['rememberLogin'] = remember
        status, data, response = self.request('/login' if legacy else '/api/v3/auth/login', payload)
        assert status == 200 and data.get('code') == 0, ('登录失败', status, data.get('code'))
        cookies = {c.name: c for c in self.jar}
        assert 'sym-ce' in cookies and 'fp_v3_refresh' in cookies, '两种 Cookie 应同时签发'
        assert cookies['sym-ce'].path == '/' and cookies['fp_v3_refresh'].path == '/api/v3/auth'
        assert all(any(k.lower() == 'httponly' for k in c._rest) and not c.domain_specified for c in cookies.values() if c.name in ('sym-ce', 'fp_v3_refresh')), [(c.name, c._rest, c.domain_specified) for c in cookies.values() if c.name in ('sym-ce', 'fp_v3_refresh')]
        if legacy:
            status, data, _ = self.request('/api/v3/auth/refresh', {})
            assert status == 200 and data['code'] == 0, ('刷新失败', status, data.get('code'), data.get('msg'))
        return data['data']['accessToken']

    def actor(self):
        status, data, _ = self.request('/api/v3/auth/session', {})
        assert status == 200 and data['code'] == 0, ('刷新失败', status, data.get('code'), data.get('msg'))
        return data['data']['userId']

first, second, other = Browser(), Browser(), Browser()
a = first.login(accounts[0])
assert first.actor() == accounts[0]['id']
assert first.request('/api/v3/me', token=a)[0] == 200
print('PASS 新版登录同时建立旧 Cookie 会话；Cookie 路径、HttpOnly、host-only 正确')
b = second.login(accounts[1], legacy=True)
assert second.actor() == accounts[1]['id']
assert second.request('/api/v3/me', token=b)[0] == 200
print('PASS 旧版登录后直接刷新取得新版访问令牌')
c = other.login(accounts[0])
assert first.request('/api/v3/auth/logout', {}, origin='https://untrusted.example')[0] == 403
assert first.request('/api/v3/auth/session', {}, origin='http://evil.localhost:8081')[0] == 403
assert first.request('/api/v3/me', token=a)[0] == 200
print('PASS 不可信来源不能查询绑定或退出会话')
saved_cookie = next(c.value for c in first.jar if c.name == 'sym-ce')
status, _, _ = first.request('/logout')
assert status == 200
assert first.actor() is None
assert first.request('/api/v3/me', token=a)[0] == 401
assert first.request('/api/v3/auth/refresh', {})[0] == 401
replay = Browser()
assert replay.request('/api/v3/auth/session', {}, cookie='sym-ce=' + saved_cookie)[1]['data']['userId'] is None
assert other.request('/api/v3/me', token=c)[0] == 200
print('PASS 旧版退出撤销同家族访问/刷新令牌；旧 Cookie 重放失败；其他浏览器保持登录')
saved_cookie = next(c.value for c in second.jar if c.name == 'sym-ce')
assert second.request('/api/v3/auth/logout', {})[0] == 200
assert second.actor() is None
assert second.request('/api/v3/me', token=b)[0] == 401
assert replay.request('/api/v3/auth/session', {}, cookie='sym-ce=' + saved_cookie)[1]['data']['userId'] is None
assert second.request('/api/v3/auth/logout', {})[0] == 200
print('PASS 新版退出使旧 Cookie 立即失效；重复退出幂等')
assert other.request('/api/v3/auth/logout', {})[0] == 200
ephemeral = Browser()
ephemeral.login(accounts[1], legacy=True, remember=False)
assert all(c.discard for c in ephemeral.jar if c.name in ('sym-ce', 'fp_v3_refresh'))
assert ephemeral.request('/api/v3/auth/logout', {})[0] == 200
print('PASS 不记住登录时刷新后仍为会话 Cookie；测试凭据未输出')

if args.fault_test:
    import subprocess
    # 固定本地开发容器，触发器只匹配临时账号，且无论断言是否成功都立即移除。
    container = json.loads(subprocess.check_output(['docker', 'inspect', 'rhythm-dev-mysql']))[0]
    environment = dict(value.split('=', 1) for value in container['Config']['Env'] if '=' in value)
    actor = accounts[0]['id']
    assert actor.isdecimal() and accounts[0]['name'].startswith('ssoLocal')
    command = ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + environment['MYSQL_ROOT_PASSWORD'],
               'rhythm-dev-mysql', 'mysql', '-uroot', environment.get('MYSQL_DATABASE', 'rhythm')]
    def sql(statement):
        result = subprocess.run(command, input=statement, text=True, capture_output=True)
        assert result.returncode == 0, '本地故障夹具执行失败'
    fault = Browser()
    token = fault.login(accounts[0])
    original = {c.name: c.value for c in fault.jar if c.name in ('sym-ce', 'fp_v3_refresh')}
    try:
        sql("DELIMITER $$\nCREATE TRIGGER sso_local_revoke_failure BEFORE UPDATE ON symphony_api_v3_token FOR EACH ROW BEGIN "
            + "IF OLD.userId='" + actor + "' AND NEW.state=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='local SSO fault'; END IF; END$$\nDELIMITER ;\n")
        assert fault.request('/api/v3/auth/logout', {})[0] == 503
        legacy_failure = fault.request('/logout')[0]
        assert legacy_failure == 503, ('旧版退出故障状态', legacy_failure)
        current = {c.name: c.value for c in fault.jar if c.name in ('sym-ce', 'fp_v3_refresh')}
        assert current == original, '撤销失败不能先清除可重试的 Cookie'
        assert fault.request('/api/v3/me', token=token)[0] == 200
    finally:
        sql('DROP TRIGGER IF EXISTS sso_local_revoke_failure;')
    assert fault.request('/api/v3/auth/logout', {})[0] == 200
    assert fault.request('/api/v3/me', token=token)[0] == 401
    print('PASS 本地定向存储故障：两端退出返回 503、保留 Cookie；解除故障后重试完成撤销')
