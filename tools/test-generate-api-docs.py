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
        self.assertEqual('#/components/schemas/ArticleStick', self.response_field('articles', 'data.list[].stick')['$ref'])
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

    def test_all_http_operations_have_a_success_representation(self):
        for path, operations in self.spec['paths'].items():
            for method, operation in operations.items():
                with self.subTest(path=path, method=method):
                    responses = operation['responses']
                    self.assertTrue(
                        any(status.startswith('2') and response.get('content') for status, response in responses.items())
                        or '101' in responses or '303' in responses
                    )

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
