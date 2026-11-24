"""从 API.md 与 Java 路由生成 Rhythm 的 v2、v3 OpenAPI。"""

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

METHOD = r'GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS'
LEGACY_REFERENCE = re.compile(rf'(?<![A-Za-z])({METHOD}|WSS|WS|WebSocket)\`?\s+\`?(/[^\s`，、；。]+)')
REFERENCE = re.compile(rf'\b({METHOD})\s+(/api/v3/[^\s`，、；。]+)')
ROUTE = re.compile(r'Dispatcher\.(get|post|put|patch|delete|head|options|webSocket)\(\s*"(/api/v3/[^"]+)"([^;]*);')
CHINESE = re.compile(r'[\u3400-\u9fff]')
KEY = r'[A-Za-z_][A-Za-z0-9_.-]*'
SCOPES = {
    'contentRead': 'content.read', 'profileRead': 'profile.read',
    'optionalContent': 'content.read', 'optionalProfile': 'profile.read',
    'settingsWrite': 'settings.write', 'activityRead': 'activity.read',
    'activityWrite': 'activity.write', 'contentWrite': 'content.write',
    'messagesRead': 'messages.read', 'messagesWrite': 'messages.write',
    'notificationsRead': 'notifications.read', 'notificationsWrite': 'notifications.write',
    'commentsWrite': 'comments.write', 'reactionsWrite': 'reactions.write',
    'followsWrite': 'follows.write', 'uploadsWrite': 'uploads.write',
    'partnerRead': 'profile.read', 'partnerWrite': 'settings.write',
}
GROUPS = {
    '通用': '接口通用约定与会话状态。',
    '账号与授权': '登录、账号安全与应用授权。',
    '文章与评论': '文章、专栏、评论及互动。',
    '聊天与通知': '聊天室、私信、通知和实时订阅。',
    '社区与个人资料': '公开资料、关系、榜单和社区内容。',
    '积分与权益': '积分、会员、商城和已购权益。',
    '文件与工具': '图片、附件和编辑工具。',
    '游戏': '游园币、宠物、停车和搭子等玩法。',
    '管理接口': '需要对应管理权限的接口。',
}
COMMON = {
    'apiKey': ('旧版接口使用的用户通用密钥。', 'string'),
    'p': ('页码，从 1 开始。', 'integer'), 'size': ('每页记录数。', 'integer'),
    'id': ('当前资源编号。', 'string'), 'userName': ('站点用户名。', 'string'),
    'userId': ('用户编号。', 'string'), 'petId': ('宠物编号。', 'string'),
    'name': ('显示名称。', 'string'), 'type': ('记录的类型。', None),
    'content': ('提交或返回的内容。', 'string'),
    'list': ('查询结果列表。', 'array'), 'pagination': ('分页信息。', 'object'),
    'page': ('当前页码。', 'integer'), 'pageSize': ('每页记录数。', 'integer'),
    'total': ('符合条件的记录总数。', 'integer'), 'pageCount': ('分页总数。', 'integer'),
    'revision': ('当前记录的修订版本。', 'integer'),
    'version': ('服务端返回的版本号。', 'integer'),
    'createdAt': ('创建时间，Unix 毫秒时间戳。', 'integer'),
    'updatedAt': ('更新时间，Unix 毫秒时间戳。', 'integer'),
    'committedAt': ('操作提交时间，Unix 毫秒时间戳。', 'integer'),
    'serverNow': ('服务端当前时间，Unix 毫秒时间戳。', 'integer'),
    'enabled': ('是否启用。', 'boolean'), 'active': ('是否有效。', 'boolean'),
    'client': ('客户端的完整配置。', 'object'),
    'productId': ('外观商城商品编号。', 'string'),
    'speciesId': ('宠物目录中的物种编号。', 'string'),
    'itemId': ('目录中的物品编号。', 'string'),
    'quantity': ('本次操作涉及的物品数量。', 'integer'),
    'catalogVersion': ('客户端读取到的目录版本。', 'integer'),
    'expectedDate': ('客户端确认的北京时间自然日。', 'string'),
    'action': ('本次提交的操作。可选值见接口说明。', 'string'),
    'operation': ('服务端已处理的操作类型。', 'string'),
    'activityId': ('活动编号。', 'string'),
    'clientId': ('下载目录中的客户端编号。', 'string'),
    'recommended': ('是否设为推荐客户端。', 'boolean'),
    'sortOrder': ('列表展示排序值。', 'integer'),
    'mountId': ('当前装备的座驾样式编号。空字符串表示未装备。', 'string'),
    'expiresAt': ('有效期截止时间，Unix 毫秒时间戳。', 'integer'),
    'balance': ('当前余额。币种由接口决定。', 'integer'),
    'data': ('响应中的业务数据。', None),
    'capabilities': ('当前身份可用的功能。', 'object'),
    'redPacket': ('红包消息的结构化数据。', 'object'),
    'weather': ('天气卡片的结构化数据。', 'object'),
    'music': ('音乐卡片的结构化数据。', 'object'),
    'primaryProfession': ('主职业信息。是否返回取决于职业隐私设置。', 'object'),
}

SUPPLEMENT = {
    ('GET', '/api/v3/me/appearance-items'): '''查询本人购买的外观权益和当前装备的座驾。
响应：
| data.list | 外观权益列表 | [] |
| - productId | 外观商城商品编号 | 字符串 |
| - expiresAt | 权益到期时间，Unix 毫秒时间戳 | 整数 |
| data.mountId | 当前有效座驾的样式编号。空字符串表示未装备 | 字符串 |
| data.serverNow | 服务端当前时间，Unix 毫秒时间戳 | 整数 |
| data.balance | 本人当前站点积分余额 | 整数 |''',
    ('PUT', '/api/v3/me/appearance-mount'): '''装备本人已购买且仍有效的座驾，或卸下当前座驾。
请求：
| productId | 必填。座驾的商城商品编号。传空字符串卸下座驾 | 字符串 |
响应：
| data.mountId | 操作完成后的座驾样式编号。空字符串表示已卸下 | 字符串 |''',
    ('GET', '/api/v3/users/{userName}/professions/summary'): '''按职业隐私设置查询主职业摘要，供会员资料卡展示。
请求：
| userName | 目标用户的用户名 | 字符串 |
响应：
| data.primaryProfession | 当前读者可见的主职业。没有设置或不可见时省略 | {} |''',
}

def normalized(path):
    return re.sub(r'\{[^{}]+\}', '{}', path.split('?')[0].rstrip(').：:'))

def typ(example):
    value = example.strip().strip('`').strip()
    if value in ('true', 'false', '布尔值', '布尔'):
        return 'boolean'
    if value.startswith('[') or value == '数组':
        return 'array'
    if value.startswith('{') or value == '对象':
        return 'object'
    if re.fullmatch(r'-?\d+', value) or value in ('整数', '毫秒时间戳'):
        return 'integer'
    if re.fullmatch(r'-?\d+\.\d+', value):
        return 'number'
    if value and value not in ('-', '—', 'null', '视情况', '可选', '必填'):
        return 'string'
    return None

def row(line):
    cells = re.split(r'(?<!\\)\|', line.strip().strip('|'))
    if len(cells) < 2:
        return None
    raw = cells[0].strip().strip('`')
    depth = len(re.match(r'^-*', raw)[0])
    key = raw[depth:].strip().strip('`')
    desc = cells[1].strip().replace('\\|', '|')
    if key in ('Key', '字段', '参数', '名称') or not re.fullmatch(KEY, key) or not CHINESE.search(desc):
        return None
    example = cells[2].strip() if len(cells) > 2 else ''
    return depth, key, desc, example

def schema_for(key, desc, example):
    kind = COMMON.get(key, (None, None))[1] if desc == COMMON.get(key, (None, None))[0] else None
    kind = kind or typ(example)
    if key.lower().endswith('id') and kind == 'integer' and not re.search('整数|数值', desc):
        kind = 'string'
    schema = {'description': desc}
    if kind:
        schema['type'] = kind
    if kind == 'array':
        schema['items'] = {}
    return schema

def put(root, path, schema):
    node = root
    for name in path[:-1]:
        if node.get('type') == 'array':
            node = node.setdefault('items', {})
        node['type'] = 'object'
        description, _ = COMMON.get(name, ('此字段包含下列子字段。', None))
        node = node.setdefault('properties', {}).setdefault(name, {'description': description})
    if node.get('type') == 'array':
        node = node.setdefault('items', {})
    node['type'] = 'object'
    field = node.setdefault('properties', {}).setdefault(path[-1], {})
    if field.get('properties') and schema.get('type') not in (None, 'object'):
        schema = {k: v for k, v in schema.items() if k != 'type'}
    field.update(schema)

def read_fields(lines):
    mode = None
    stack = {}
    fence = False
    for line in lines:
        if line.startswith('```'):
            fence = not fence
        if fence:
            continue
        heading = line.strip().lstrip('>').strip().strip('*')
        if re.match(r'^(?:请求(?:参数|体|头|字段|示例)?|路径参数|查询参数)[：:]|^请求字段如下', heading):
            mode, stack = 'request', {}
        elif re.match(r'^(?:成功)?响应(?:字段|示例|体|头)?[：:]|^(?:成功)?返回(?:字段|示例)?[：:]', heading):
            mode, stack = 'response', {}
        if mode and line.lstrip().startswith('|') and (item := row(line)):
            depth, key, description, example = item
            path = (*stack.get(depth - 1, ()), *key.split('.'))
            stack[depth] = path
            yield mode, path, description, example

def sections(markdown):
    """返回实际接口段落，保留小节原文供阅读。"""
    group, heading, part, fence = '', '', [], False
    for line in markdown.splitlines():
        if line.startswith('```'):
            fence = not fence
        if not fence and (match := re.match(r'^(#{3,6}) (.+)', line)):
            if part:
                yield group, heading, part
            if len(match[1]) == 3:
                group = match[2]
            heading, part = match[2], []
        else:
            part.append(line)
    if part:
        yield group, heading, part

def inline_fields(lines):
    """处理游戏等使用内联字段清单的小节。"""
    for line in lines:
        for match in re.finditer(r'`\{([^{}`]+)\}`', line):
            before = line[:match.start()].rstrip()
            if re.search(r'返回|回执|响应|结果|data\s*为', before[-32:]):
                mode = 'response'
            elif re.search(r'请求|提交|参数|[：:]$', before[-36:]) and ('| `' not in before[-6:]):
                mode = 'request'
            else:
                continue
            for raw in re.split(r',\s*', match[1]):
                field, _, value = raw.strip().partition(':')
                field = field.rstrip('?')
                if re.fullmatch(KEY, field):
                    yield mode, (field,), None, value

def references(lines, version):
    fence = False
    for index, line in enumerate(lines):
        if line.startswith('```'):
            fence = not fence
        elif not fence:
            adjusted = line
            if version == 'v2':
                adjusted = re.sub(r'(?<=`)wss?://[^/\s`]+(/[^\s`]+)', r'WSS \1', adjusted)
            adjusted = re.sub(r'\|\s*(' + METHOD + r')\s*\|\s*`', r' \1 ', adjusted)
            pattern = REFERENCE if version == 'v3' else LEGACY_REFERENCE
            for match in pattern.finditer(adjusted):
                method = 'GET' if match[1] in ('WSS', 'WS', 'WebSocket') else match[1]
                path = normalized(match[2].replace('\\', ''))
                if version == 'v3' or not path.startswith('/api/v3/'):
                    yield index, (method, path), line

def summary_for(label, declaration, route_count):
    if route_count <= 1 and CHINESE.search(label):
        return label
    tail = declaration.split('`：', 1)
    if len(tail) == 2:
        sentence = re.split(r'[。；]', tail[1])[0].strip()
        if CHINESE.search(sentence) and not sentence.startswith('`') and len(sentence) <= 45:
            return sentence
    return label or 'API v3 接口'


def schema_field(schema, path):
    """定位已记录字段。找不到时立即报错，避免重命名后留下无效补充。"""
    node = schema
    for segment in path.split('.'):
        if segment.endswith('[]'):
            node = node['properties'][segment[:-2]]['items']
        else:
            node = node['properties'][segment]
    return node


def apply_contract(operation, contract, operation_key):
    """补充 Markdown 无法表达的 OpenAPI 结构。"""
    json_media = 'application/json'
    try:
        unknown = contract.keys() - {'request', 'requestBody', 'idempotencyKey', 'data', 'responses', 'fields'}
        if unknown or {'data', 'responses'} <= contract.keys() or {'request', 'requestBody'} <= contract.keys():
            raise ValueError(f'{operation_key} 的 OpenAPI 补充格式不正确：{sorted(unknown)}')
        if 'request' in contract and contract['request'] is None:
            operation.pop('requestBody', None)
        elif 'request' in contract:
            operation['requestBody'] = {'required': True, 'content': {
                json_media: {'schema': contract['request']},
            }}
        if 'requestBody' in contract:
            operation['requestBody'] = contract['requestBody']
        if contract.get('idempotencyKey'):
            parameters = operation.setdefault('parameters', [])
            for parameter in parameters:
                if parameter['in'] == 'header' and parameter['name'].lower() == 'idempotency-key':
                    parameter['required'] = True
                    break
            else:
                parameters.append(make_param('Idempotency-Key', 'header',
                                             '幂等键。同一操作重试应沿用原键和原请求体。', 'string', True))
        if 'responses' in contract:
            operation['responses'] = {
                **{status: response for status, response in operation['responses'].items()
                   if status == 'default'},
                **contract['responses'],
            }
        if 'data' in contract:
            success = operation['responses']['2XX']
            content = success.setdefault('content', {})
            content[json_media] = {'schema': {
                'type': 'object',
                'properties': {
                    'code': {'type': 'integer', 'const': 0},
                    'msg': {'type': 'string'},
                    'data': contract['data'],
                },
            }}
        for key, fields in contract.get('fields', {}).items():
            if key == 'request':
                carrier = operation['requestBody']
            elif key == 'response':
                carrier = operation['responses']['2XX']
            else:
                raise ValueError(f'未知字段位置 {key}')
            schema = carrier['content'][json_media]['schema']
            for path, rules in fields.items():
                target = schema_field(schema, path)
                description = target.get('description')
                target.clear()
                if description:
                    target['description'] = description
                target.update(rules)
    except KeyError as error:
        raise ValueError(f'{operation_key} 的 OpenAPI 补充与生成结构不匹配：{error}') from error


def check_schema_refs(spec):
    components = spec['components'].get('schemas', {})

    def visit(node):
        if isinstance(node, list):
            for value in node:
                visit(value)
        elif isinstance(node, dict):
            ref = node.get('$ref')
            if ref is not None:
                prefix = '#/components/schemas/'
                if not ref.startswith(prefix) or ref[len(prefix):] not in components:
                    raise ValueError(f'未知 OpenAPI 结构引用：{ref}')
            for value in node.values():
                visit(value)

    visit(spec['paths'])
    visit(components)


def compile_spec(rhythm, version):
    source = (rhythm / 'API.md').read_text(encoding='utf-8').split('## API v3\n', 1)[1]
    current, legacy = source.split('## 旧版及兼容 API', 1)
    markdown = (current if version == 'v3' else legacy).strip()
    inventory = {}
    java = rhythm / 'src/main/java'
    for path in sorted(java.rglob('*.java')):
        for method, url, registration in ROUTE.findall(path.read_text(encoding='utf-8')):
            key = ('GET' if method == 'webSocket' else method.upper(), normalized(url))
            inventory[key] = (url, registration, method == 'webSocket')
    if not inventory:
        raise ValueError('没有找到 Rhythm 的 API v3 路由')

    indexed = defaultdict(list)
    terms = defaultdict(Counter)
    for group, title, lines in sections(markdown):
        refs = list(references(lines, version))
        exact = [(i, key, line) for i, key, line in refs if line.startswith('`') and line.rstrip().endswith('`') and line.count('`') in (2, 4)]
        for mode, path, desc, example in read_fields(lines):
            terms[path[-1]][(desc, example)] += 1
        for i, key, line in refs:
            end = next((j for j, _, _ in exact if j > i), len(lines))
            if len(refs) == 1:
                excerpt = lines
            elif exact and line in [l for _, _, l in exact]:
                excerpt = lines[i:end]
            else:
                excerpt = [line]
            indexed[key].append((group, title, line, excerpt, len(refs), lines))

    if version == 'v2':
        # 旧版包含非 /api 路径和历史协议，以文档明确声明的接口为边界。
        inventory = {}
        for group, title, lines in sections(markdown):
            for _, key, line in references(lines, version):
                if not line.startswith(('`', '|', '- `')) or '*' in key[1]:
                    continue
                declaration = re.sub(r'(?<=`)wss?://[^/\s`]+(/[^\s`]+)', r'WSS \1', line)
                match = LEGACY_REFERENCE.search(declaration)
                if not match:
                    continue
                url = match[2].split('?')[0].rstrip(').：:').replace('\\', '')
                url = re.sub(r'(?<=/):([A-Za-z][A-Za-z0-9_]*)', r'{\1}', url)
                if not re.fullmatch(r'/[A-Za-z0-9_./{}~-]+', url):
                    continue
                ws = match[1] in ('WSS', 'WS', 'WebSocket')
                inventory.setdefault(key, (url, '', ws))

    if not inventory:
        raise ValueError(f'{version} 没有可生成的接口，请检查 API.md 与路由来源')
    contract_data = {}
    if version == 'v3':
        contract_path = rhythm / 'docs/api-v3-openapi-schemas.json'
        contract_data = json.loads(contract_path.read_text(encoding='utf-8'))
    contracts = contract_data.get('operations', {}).copy()
    paths, stats = {}, Counter()
    for (method, canonical), (url, registration, ws) in sorted(inventory.items()):
        pd = re.findall(r'\{([^{}]+)\}', url)
        matches = indexed[(method, canonical)]
        if ws and version == 'v3':
            handshake = f'`{method} {url}`'
            matches = [match for match in matches if match[2] == handshake]
        heading = matches[0][1] if matches else ''
        title = summary_for(heading, matches[0][2], matches[0][4]) if matches else '连接实时 WebSocket'
        tag = (matches[0][0] or '通用') if matches else '聊天与通知'
        body, response = {'type': 'object'}, {'type': 'object'}
        params = {}
        excerpts = []
        supplement = SUPPLEMENT.get((method, url))
        if supplement:
            title = supplement.splitlines()[0].rstrip('。')
            tag = '积分与权益' if 'appearance' in url else '社区与个人资料'
            matches = [(tag, title, '', supplement.splitlines(), 1, supplement.splitlines()), *matches]

        for _, _, declaration, excerpt, _, context in matches:
            if (block := '\n'.join(excerpt).strip()) and block not in excerpts:
                excerpts.append(block)
            if len(excerpt) == 1 and len(context) <= 25 and (heading_block := '\n'.join(context).strip()) not in excerpts:
                excerpts.append(heading_block)
            if ws and version == 'v3':
                continue  # WebSocket 帧不属于 HTTP 请求参数。
            offsets = re.search(r'/[^\s`?]+\?([^\s`，、；]+)', declaration)
            query_keys = set(re.findall(r'(?:^|&)(' + KEY + r')=', offsets[1])) if offsets else set()
            for name in sorted(query_keys):
                desc, kind = COMMON.get(name, ('按接口说明筛选返回结果。', 'string'))
                params.setdefault(('query', name), make_param(name, 'query', desc, kind))
            explicit = list(read_fields(excerpt))
            for mode, fp, desc, ex in [
                *inline_fields(excerpt), *explicit
            ]:
                field = fp[-1]
                if desc is None:
                    desc, hint = COMMON.get(field, (None, None))
                    if not desc:
                        desc = next((entry[0] for entry, _ in terms[field].most_common(1)), None)
                    if not desc:
                        continue
                else:
                    hint = None
                s = schema_for(field, desc, ex)
                if hint:
                    s.setdefault('type', hint)
                if mode == 'response':
                    put(response, fp if version == 'v2' or fp[0] in ('data', 'code', 'msg') else ('data', *fp), s)
                elif field in pd or '路径参数' in desc:
                    if field in pd:
                        params['path', field] = make_param(field, 'path', desc, 'string')
                elif field.lower() in ('authorization', 'content-type', 'accept'):
                    continue
                elif field == 'Idempotency-Key' or '请求头' in desc:
                    params['header', field] = make_param(field, 'header', desc, s.get('type'), '必填' in desc)
                elif method == 'GET' or '查询参数' in desc or field in query_keys:
                    if len(fp) == 1:
                        params['query', field] = make_param(field, 'query', desc, s.get('type'), '必填' in desc)
                else:
                    put(body, fp, s)
                    if len(fp) == 1 and re.search(r'必填|必需|必须提供', desc):
                        if field not in body.setdefault('required', []):
                            body['required'].append(field)
        for name in pd:
            desc = COMMON.get(name, (None,))[0]
            if not desc:
                desc = next((d for d, _ in terms[name] if '路径参数' in d), None)
            params.setdefault(('path', name), make_param(name, 'path', desc or '目标资源的路径标识，使用对应列表或详情返回的编号。', 'string'))

        prose = '\n\n'.join(excerpts)
        if not CHINESE.search(prose):
            prose = f'{title}。\n\n{prose}'.strip()
        if not matches and not ws:
            raise ValueError(f'缺少接口说明：{method} {url}')
        if ws and version == 'v3' and not matches:
            prose = ('使用对应实时票据接口获取一次性票据，建立 WebSocket 连接后通过首帧提交。'
                     '事件字段和重连方式参见完整说明中的文章、聊天室、私信及通知实时订阅章节。')
        scope_match = re.search(r'auth::(\w+)', registration)
        scope = SCOPES.get(scope_match[1]) if scope_match else None
        optional = scope_match and scope_match[1].startswith('optional')
        if 'writes::handle' in registration and 'Idempotency-Key' in prose:
            params.setdefault(('header', 'Idempotency-Key'), make_param('Idempotency-Key', 'header', '幂等键。同一操作重试应沿用原键和原请求体。', 'string', True))
        operation_id = method.lower() + '_' + re.sub(r'[^A-Za-z0-9]+', '_', url.removeprefix('/api/v3/')).strip('_')
        operation = {'operationId': operation_id, 'tags': [tag], 'summary': title, 'description': prose,
                     'externalDocs': {'description': '完整中文说明与示例', 'url': f'./api-docs/{version}-reference'},
                     'responses': {}}
        if scope:
            operation['description'] = f'权限范围为 `{scope}`。\n\n' + prose
            operation['security'] = ([{}] if optional else []) + [{'Bearer': []}]
        elif scope_match and scope_match[1] == 'handle':
            operation['security'] = [{'Bearer': []}]
        if params:
            operation['parameters'] = list(params.values())
        if body.get('properties'):
            operation['requestBody'] = {'content': {'application/json': {'schema': body}}}
        success = {'description': 'WebSocket 握手成功。' if ws else '请求成功，具体业务结果见接口说明。'}
        if response.get('properties') and not ws:
            if version == 'v3' and not url.startswith('/api/v3/oauth/'):
                for name, description, kind in (
                    ('code', '成功时为 0。', 'integer'), ('msg', '服务端提示信息。', 'string'),
                    ('data', '此接口的业务结果。', None),
                ):
                    prop = response['properties'].setdefault(name, {})
                    prop.setdefault('description', description)
                    if kind:
                        prop.setdefault('type', kind)
            success['content'] = {'application/json': {'schema': response}}
        operation['responses']['101' if ws else '2XX'] = success
        if not ws:
            operation['responses']['default'] = {'description': '调用失败。请结合 HTTP 状态码与业务错误信息处理，特殊响应格式见接口说明。'}
        operation_key = f'{method} {url}'
        if operation_key in contracts:
            apply_contract(operation, contracts.pop(operation_key), operation_key)
        if 'requestBody' in operation:
            stats['request'] += 1
        if any(response.get('content') for status, response in operation['responses'].items()
               if status.startswith('2')):
            stats['response'] += 1
        paths.setdefault(url, {})[method.lower()] = operation
    if contracts:
        raise ValueError(f'OpenAPI 补充引用了未注册的操作：{", ".join(sorted(contracts))}')
    count = sum(len(methods) for methods in paths.values())
    groups = GROUPS if version == 'v3' else {
        name: f'旧版{name}接口。' for name in dict.fromkeys(op['tags'][0] for methods in paths.values() for op in methods.values())
    }
    spec = {
        'openapi': '3.1.0',
        'info': {'title': f'摸鱼派 API {version} 开发文档', 'version': version,
                 'description': (
                     '旧版及兼容接口。请根据各接口说明传入 apiKey、网页登录凭据或应用授权。'
                     if version == 'v2' else '当前版本化接口。请按各接口的要求使用 Bearer 令牌与权限范围。'
                 ) + f'\n\n[完整中文说明与示例](./api-docs/{version}-reference)'},
        'servers': [{'url': 'https://fishpi.cn', 'description': '摸鱼派正式环境'}],
        'tags': [{'name': group, 'description': description} for group, description in groups.items()],
        'paths': paths,
        'components': {'securitySchemes': {'Bearer': {'type': 'http', 'scheme': 'bearer', 'description': '在 Authorization 请求头使用 Bearer 访问令牌。'}}},
    }
    if version == 'v2':
        del spec['components']
    else:
        spec['components']['schemas'] = contract_data.get('schemas', {})
        check_schema_refs(spec)
    return spec, markdown, count, stats

def make_param(name, location, description, kind=None, required=False):
    return {'name': name, 'in': location, 'description': description,
            'required': location == 'path' or required, 'schema': {'type': kind or 'string'}}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('rhythm', type=Path, help='包含 API v3 源码与 API.md 的 Rhythm 工作区')
    parser.add_argument('output', type=Path, help='生成资源所在的目录')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for version in ('v2', 'v3'):
        spec, markdown, count, stats = compile_spec(args.rhythm, version)
        (args.output / f'{version}.json').write_text(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        (args.output / f'{version}.md').write_text(
            f'# 摸鱼派 API {version} 开发文档\n\n' + markdown + '\n', encoding='utf-8')
        print(f"{version}：{count} 个操作，{stats['request']} 个结构化请求体，{stats['response']} 个结构化响应。")


if __name__ == '__main__':
    main()
