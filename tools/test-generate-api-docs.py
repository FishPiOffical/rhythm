"""回归检查 OpenAPI 类型补充和不同协议的成功响应。"""

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOADER = importlib.util.spec_from_file_location('generate_api_docs', ROOT / 'tools/generate-api-docs.py')
generator = importlib.util.module_from_spec(LOADER)
LOADER.loader.exec_module(generator)


class OpenApiSchemaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec, _, _, _ = generator.compile_spec(ROOT, 'v3')

    def operation(self, path, method='get'):
        return self.spec['paths'][f'/api/v3/{path}'][method]

    def response_field(self, path, field):
        schema = self.operation(path)['responses']['2XX']['content']['application/json']['schema']
        return generator.schema_field(schema, field)

    def test_nullable_and_shared_fields(self):
        self.assertEqual(['boolean', 'null'], self.response_field('articles/{articleId}', 'data.commentable')['type'])
        self.assertEqual(['string', 'null'], self.response_field('me/pet/brawl/orders', 'data.next')['type'])
        article = self.response_field('articles', 'data.list[]')
        self.assertEqual('#/components/schemas/ArticleSummary', article['$ref'])
        self.assertEqual('#/components/schemas/ArticleStick',
                         self.spec['components']['schemas']['ArticleSummary']['properties']['stick']['$ref'])
        detail = self.response_field('me/pet/brawl/orders/{id}', 'data')
        self.assertEqual('#/components/schemas/PetBrawlOrder', detail['allOf'][0]['$ref'])
        self.assertIn('balance', detail['allOf'][1]['properties'])
        body = self.operation('admin/articles', 'post')['requestBody']['content']['application/json']['schema']
        self.assertEqual(['integer', 'string', 'null'], [
            part['type'] for part in generator.schema_field(body, 'chapterNo')['anyOf']
        ])

    def test_request_shapes_for_drafts_user_update_and_brawl_upgrade(self):
        def json_body(path, method):
            operation = self.operation(path, method)
            headers = {param['name']: param for param in operation['parameters'] if param['in'] == 'header'}
            self.assertTrue(headers['Idempotency-Key']['required'], path)
            body = operation['requestBody']
            self.assertTrue(body['required'], path)
            return body['content']['application/json']['schema']

        create_draft = json_body('me/drafts', 'post')
        self.assertEqual(create_draft, json_body('me/drafts/{draftId}', 'put'))
        self.assertEqual('#/components/schemas/DraftSaveInput', create_draft['$ref'])
        draft = self.spec['components']['schemas']['DraftSaveInput']
        self.assertNotIn('required', draft)
        self.assertEqual('string', draft['properties']['chapterNo']['type'])
        self.assertEqual('boolean', draft['properties']['notifyFollowers']['type'])
        self.assertEqual(16, len(draft['properties']))

        user_update = json_body('admin/users/{userId}', 'put')
        self.assertEqual(1, user_update['minProperties'])
        self.assertEqual({'userNickname', 'userURL', 'userIntro', 'userTags', 'userEmail',
                          'userStatus', 'userCommentStatus', 'userPointStatus', 'userRole'},
                         set(user_update['properties']))
        self.assertNotIn('required', user_update)

        upgrade = json_body('me/pet/brawl/upgrade', 'post')
        self.assertEqual({'petId', 'weapon', 'level', 'version'}, set(upgrade['required']))
        self.assertIn('LOTUS', upgrade['properties']['weapon']['enum'])
        self.assertEqual('integer', upgrade['properties']['level']['type'])
        for path in ('admin/articles/reindex', 'admin/articles/{articleId}/reindex'):
            reindex = self.operation(path, 'post')
            self.assertNotIn('requestBody', reindex)
            self.assertTrue(any(param['name'] == 'Idempotency-Key' and param['in'] == 'header' and param['required']
                                for param in reindex['parameters']), path)
        self.assertFalse(self.operation('me/pet').get('parameters'))

    def test_write_audit_request_bodies_and_headers(self):
        for method, path, fields, idempotency in (
            ('post', 'admin/medals/batch-grant', {'medalId', 'targets', 'expireTime', 'data'}, True),
            ('post', 'admin/medals/hold-check', {'medalId', 'targets'}, False),
            ('post', 'admin/professions/scheme/draft', {'professionId', 'professionRevisionId',
                     'migrationPolicy', 'rewardMigrationPolicy', 'migrationConfigJson', 'levels'}, False),
            ('post', 'me/pet/brawl/orders', {'petId', 'weapon', 'level', 'version', 'materials', 'product'}, True),
            ('put', 'me/pet/brawl/socket', {'petId', 'weapon', 'version', 'revision', 'slot', 'item'}, True),
            ('post', 'me/pet/activities', {'petId', 'kind', 'entryId', 'activityVersion'}, True),
        ):
            with self.subTest(path=path):
                operation = self.operation(path, method)
                body = operation['requestBody']
                self.assertTrue(body['required'])
                self.assertEqual(fields, set(body['content']['application/json']['schema']['properties']))
                headers = [p for p in operation.get('parameters', []) if p['name'] == 'Idempotency-Key']
                self.assertEqual(idempotency, bool(headers))
                if headers:
                    self.assertEqual(1, len(headers))
                    self.assertTrue(headers[0]['required'])

        claim = self.operation('me/pet/brawl/season/{id}/claim', 'post')['requestBody']
        self.assertTrue(claim['required'])
        self.assertEqual('#/components/schemas/EmptyJsonObject', claim['content']['application/json']['schema']['$ref'])
        self.assertNotIn('requestBody', self.operation('chat/realtime-ticket', 'post'))
        uncollect = self.operation('me/collections/{targetId}', 'delete')
        self.assertNotIn('requestBody', uncollect)
        self.assertTrue(any(p['name'] == 'Idempotency-Key' and p['required']
                            for p in uncollect['parameters']))
        bubble = self.operation('admin/appearance/bubbles/{bubbleId}', 'put')['requestBody']
        save, publish = bubble['content']['application/json']['schema']['oneOf']
        self.assertIn('draft', save['required'])
        self.assertNotIn('draft', publish['properties'])

    def test_admin_fish_game_icon_field_compatibility(self):
        schema_ref = '#/components/schemas/AdminFishGameInput'
        for method, path in (('post', 'admin/fish-games'), ('put', 'admin/fish-games/{gameId}')):
            with self.subTest(method=method):
                body = self.operation(path, method)['requestBody']
                self.assertEqual(schema_ref, body['content']['application/json']['schema']['$ref'])
        model = self.spec['components']['schemas']['AdminFishGameInput']
        self.assertEqual('uri', model['properties']['fishGameIconUrl']['format'])
        self.assertTrue(model['properties']['fishGameIcon']['deprecated'])
        self.assertEqual([['fishGameIconUrl'], ['fishGameIcon']],
                         [variant['required'] for variant in model['anyOf']])

    def test_write_audit_non_json_content(self):
        for path in ('oauth/token', 'oauth/consent', 'oauth/revoke'):
            with self.subTest(path=path):
                body = self.operation(path, 'post')['requestBody']
                self.assertTrue(body['required'])
                self.assertEqual({'application/x-www-form-urlencoded'}, set(body['content']))
        token = self.operation('oauth/token', 'post')['requestBody']['content']
        code, refresh = token['application/x-www-form-urlencoded']['schema']['oneOf']
        self.assertIn('code_verifier', code['required'])
        self.assertIn('refresh_token', refresh['required'])
        self.assertNotIn('code_verifier', refresh['properties'])
        image = self.operation('me/identity-applications/enterprise', 'post')['requestBody']
        self.assertTrue(image['required'])
        self.assertEqual({'image/png', 'image/jpeg'}, set(image['content']))
        for media_type in image['content'].values():
            self.assertEqual('binary', media_type['schema']['format'])

    def test_optional_empty_json_distinct_from_required_body(self):
        for path in ('admin/medals/sponsor/reorder', 'admin/tags/remove-unused',
                     'me/game-saves/{gameId}/migrations'):
            with self.subTest(path=path):
                body = self.operation(path, 'post')['requestBody']
                self.assertFalse(body['required'])
                self.assertEqual('#/components/schemas/EmptyJsonObject',
                                 body['content']['application/json']['schema']['$ref'])
        self.assertEqual(0, self.spec['components']['schemas']['EmptyJsonObject']['maxProperties'])

    def test_paged_content_and_conditional_comment_root(self):
        for path in (
            'articles/{articleId}/comments', 'articles/{articleId}/paragraph-comments',
            'articles/{articleId}/paragraphs', 'cities/{cityName}/users', 'fish-games/{gameId}/comments',
            'me/columns', 'me/identity-applications', 'me/points', 'me/watching/breezemoons',
            'review/identity-applications',
        ):
            with self.subTest(path=path):
                self.assertEqual('array', self.response_field(path, 'data.list')['type'])
                self.assertTrue(self.response_field(path, 'data.list[]'))
        comments = self.operation('articles/{articleId}/comments')
        root_id = next(p for p in comments['parameters'] if p['name'] == 'rootId')
        self.assertFalse(root_id['required'])
        self.assertIn('view=replies', root_id['description'])

    def test_chat_string_cursors_and_optional_snapshots(self):
        for path in ('chat/messages', 'chat/messages/{messageId}/context', 'me/conversations/{peerId}/messages'):
            with self.subTest(path=path):
                for name in ('nextBefore', 'nextAfter'):
                    self.assertEqual('string', self.response_field(path, f'data.{name}')['type'])
        components = self.spec['components']['schemas']
        chat_ref = '#/components/schemas/ChatMessage'
        for path, field in (('chat/messages', 'data.list[]'), ('chat/messages/{messageId}', 'data'),
                            ('chat/messages/{messageId}/context', 'data.list[]')):
            self.assertEqual(chat_ref, self.response_field(path, field)['$ref'])
        chat = components['ChatMessage']['properties']
        self.assertEqual('string', chat['tail']['properties']['text']['type'])
        self.assertEqual('string', chat['petStatus']['properties']['petId']['type'])
        notification = components['Notification']['properties']
        self.assertEqual('#/components/schemas/ArticleSummary', notification['article']['$ref'])
        self.assertEqual('#/components/schemas/PublicAuthor', notification['actor']['$ref'])

    def test_realtime_tickets_do_not_require_an_idempotency_header(self):
        for path in ('realtime/tickets', 'me/messages/realtime-ticket', 'chat/realtime-ticket',
                     'articles/{articleId}/realtime-ticket', 'breezemoons/realtime-ticket'):
            with self.subTest(path=path):
                ticket = self.operation(path, 'post')
                self.assertFalse(any(param['in'] == 'header' and param['name'].lower() == 'idempotency-key'
                                     for param in ticket.get('parameters', [])))
        article_ticket = self.operation('articles/{articleId}/realtime-ticket', 'post')
        self.assertTrue(any(param['in'] == 'path' and param['name'] == 'articleId' and param['required']
                            for param in article_ticket['parameters']))

    def test_realtime_handshake_documents_frames_as_websocket(self):
        socket = self.operation('realtime')
        self.assertEqual({'101'}, set(socket['responses']))
        self.assertNotIn('parameters', socket)
        self.assertNotIn('requestBody', socket)
        for frame in ('authentication.required', 'ready', 'ping', 'pong', 'chat.presence',
                      'article.changed', 'notifications.changed', 'messages.changed', 'breezemoons.changed'):
            self.assertIn(f'`{frame}`', socket['description'])
        self.assertIn('60 秒', socket['description'])

    def test_success_media_matches_protocol(self):
        for path, method, media in (
            ('me/exports/posts/{jobId}/download', 'get', {'application/zip'}),
            ('me/medals/image', 'get', {'image/svg+xml'}),
            ('review/identity-applications/{applicationId}/material/open', 'post', {'image/jpeg', 'image/png'}),
            ('oauth/authorize', 'get', {'text/html'}),
        ):
            with self.subTest(path=path):
                responses = self.operation(path, method)['responses']
                self.assertEqual(media, set(responses['200']['content']))
                self.assertNotIn('2XX', responses)
        consent = self.operation('oauth/consent', 'post')['responses']
        self.assertIn('Location', consent['303']['headers'])
        self.assertNotIn('2XX', consent)
        self.assertEqual({'101'}, set(self.operation('realtime')['responses']))

    def test_session_unlinked_and_detailed_collection_schemas(self):
        session = self.operation('auth/session', 'post')['responses']['2XX']['content']['application/json']['schema']
        linked = session['properties']['data']['oneOf'][0]['properties']['linked']
        self.assertEqual('boolean', linked['type'])
        self.assertIs(linked['const'], False)
        self.assertTrue(linked['description'])
        self.assertNotIn('requestBody', self.operation('admin/articles/{articleId}/reindex', 'post'))
        components = self.spec['components']['schemas']
        self.assertEqual(2, len(components['PetJournalPage']['properties']['list']['items']['oneOf']))
        for name in ('revisions', 'schemes', 'automations'):
            self.assertIn('$ref', components['ProfessionVersionTree']['properties'][name]['items'])

    def test_saved_items_and_cancel_stick_have_explicit_types(self):
        components = self.spec['components']['schemas']
        for path in ('me/collections', 'me/subscriptions'):
            self.assertEqual('#/components/schemas/SavedArticle',
                             self.response_field(path, 'data.list[]')['$ref'])
        available = components['SavedArticleAvailable']['allOf'][1]['properties']['available']
        unavailable = components['SavedArticleUnavailable']['properties']['available']
        for field, expected in ((available, True), (unavailable, False)):
            self.assertEqual('boolean', field['type'])
            self.assertIs(field['const'], expected)
            self.assertTrue(field['description'])
        receipt = self.operation('admin/articles/{articleId}/cancel-stick', 'post')
        schema = receipt['responses']['2XX']['content']['application/json']['schema']
        stick = generator.schema_field(schema, 'data.articleStick')
        self.assertEqual('integer', stick['type'])
        self.assertEqual(0, stick['const'])
        self.assertTrue(stick['description'])

    def test_all_http_operations_have_a_success_representation(self):
        for path, operations in self.spec['paths'].items():
            for method, operation in operations.items():
                with self.subTest(path=path, method=method):
                    responses = operation['responses']
                    self.assertTrue(
                        any(status.startswith('2') and response.get('content') for status, response in responses.items())
                        or '101' in responses or '303' in responses
                    )

    def test_structured_objects_arrays_and_maps(self):
        for path, field, component in (
            ('admin/domains', 'items', 'AdminDomainWithTags'),
            ('admin/medals/users/{target}', 'items', 'AdminHeldMedal'),
            ('pets/catalog', 'jobs', 'PetCatalogJob'),
        ):
            item = self.response_field(path, f'data.{field}[]')
            self.assertEqual(f'#/components/schemas/{component}', item['$ref'])
            self.assertTrue(self.spec['components']['schemas'][component]['properties'])
        self.assertEqual('integer', self.response_field('admin/security/monitor', 'data.counts')['additionalProperties']['type'])
        self.assertEqual(4, len(self.spec['components']['schemas']['ChatMessageCard']['oneOf']))

        def check(schema, path):
            kinds = schema.get('type', [])
            if isinstance(kinds, str):
                kinds = [kinds]
            if 'object' in kinds:
                self.assertTrue(
                    schema.get('properties') or 'additionalProperties' in schema or schema.get('maxProperties') == 0,
                    path,
                )
            if 'array' in kinds:
                self.assertTrue(schema.get('items'), path)
            for name, child in schema.get('properties', {}).items():
                check(child, f'{path}.{name}')
            for keyword in ('items', 'additionalProperties'):
                if isinstance(schema.get(keyword), dict):
                    check(schema[keyword], f'{path}.{keyword}')
            for keyword in ('allOf', 'anyOf', 'oneOf'):
                for index, child in enumerate(schema.get(keyword, [])):
                    check(child, f'{path}.{keyword}[{index}]')

        for name, schema in self.spec['components']['schemas'].items():
            check(schema, name)
        for path, operations in self.spec['paths'].items():
            for method, operation in operations.items():
                for payload in [operation.get('requestBody', {}), *operation['responses'].values()]:
                    for entry in payload.get('content', {}).values():
                        check(entry['schema'], f'{method} {path}')

    def test_invalid_overlays_fail(self):
        operation = self.operation('articles')
        with self.assertRaisesRegex(ValueError, 'GET /articles'):
            generator.apply_contract(operation, {'fields': {'response': {'data.missing': {'type': 'string'}}}}, 'GET /articles')
        with self.assertRaisesRegex(ValueError, 'MissingDto'):
            generator.check_schema_refs({
                'components': {'schemas': {}},
                'paths': {'schema': {'$ref': '#/components/schemas/MissingDto'}},
            })


if __name__ == '__main__':
    unittest.main()
