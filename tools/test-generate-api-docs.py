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
