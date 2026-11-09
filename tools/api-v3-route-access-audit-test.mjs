import assert from 'node:assert/strict'
import {callArguments, access, scopedMethods} from './api-v3-route-access-audit.mjs'

const route = {method: 'PUT', path: '/api/v3/me/settings/profile'},
  scopes = {settingsWrite: 'settings.write', contentRead: 'content.read', activityWrite: 'activity.write'}

let args = callArguments('Dispatcher.put("/api/v3/x",p::save,auth::settingsWrite,writes::handle)', 14)
assert.deepEqual(args, ['"/api/v3/x"', 'p::save', 'auth::settingsWrite', 'writes::handle']);
assert.equal(access(route, args, scopes).kind, 'required-bearer');
console.log('PASS route access 1 explicit scoped protected chain')

args = callArguments('( "x,y", handler(a,b), x -> { nested(a,b); }, auth::contentRead, auth::activityWrite )', 0);
assert.equal(args.length, 5);
assert.deepEqual(access(route, args, scopes).requiredScopes, ['content.read', 'activity.write']);
console.log('PASS route access 2 nested calls/lambda and compound scopes retain argument boundaries')

assert.equal(access(route, ['"x"', 'p::me', 'auth::handle'], scopes).kind, 'required-bearer');
console.log('PASS route access 3 authenticated identity without a single scope stays protected')

assert.equal(access(route, ['"x"', 'p::list', 'auth::optionalProfile', 'anonymous::handle'], scopes).kind, 'optional-bearer');
assert.throws(() => access(route, ['"x"', 'p::list', 'auth::optionalProfile'], scopes));
console.log('PASS route access 4 optional credentials require explicit anonymous gate')

assert.equal(access(route, ['"x"', 'p::login', 'anonymous::handle'], scopes).kind, 'anonymous-gate');
assert.throws(() => access(route, ['"x"', 'p::save', 'auth::unknown'], scopes));
assert.throws(() => access(route, ['"x"', 'p::save'], scopes));
console.log('PASS route access 5 unknown auth and bare handlers cannot silently pass')

assert.equal(access({method: 'POST', path: '/api/v3/oauth/token'}, ['"x"', 'p::token', 'p::protocolToken'], scopes).kind, 'reviewed-oauth-protocol');
assert.throws(() => access(route, ['"x"', 'p::save', 'p::protocolToken'], scopes));
console.log('PASS route access 6 protocol exceptions only match exact approved endpoints and hooks')

assert.equal(access({method: 'WEBSOCKET', path: '/api/v3/realtime'}, ['"x"', 'channel'], scopes).kind, 'reviewed-websocket-protocol');
assert.throws(() => access({method: 'WEBSOCKET', path: '/api/v3/other'}, ['"x"', 'channel'], scopes));
assert.throws(() => callArguments('("unfinished"', 0));
console.log('PASS route access 7 WebSocket remains explicit separate review and malformed source fails')

const compact = 'public void contentRead(final RequestContext context) { required(context, "content.read"); }'
const expanded = `public void contentRead(final RequestContext context) {
    required(context, "content.read");
}`
assert.deepEqual(scopedMethods(compact), {contentRead: 'content.read'});
assert.deepEqual(scopedMethods(expanded), scopedMethods(compact));
assert.deepEqual(scopedMethods(expanded.replaceAll('\n', '\r\n')), scopedMethods(compact));
assert.deepEqual(scopedMethods(`public\tvoid\tcontentRead (
    final RequestContext context
) {
    required (
        context , "content.read"
    ) ;
}`), scopedMethods(compact));
console.log('PASS route access 8 compact, IDEA multiline, CRLF and wrapped parameters retain scopes')

const withComments = `// ${compact}
/* ${compact} */
public void settingsWrite(final RequestContext context) {
    // The wrapper still requires this scope.
    required(/* actor */ context, "settings.write");
}`
assert.deepEqual(scopedMethods(withComments), {settingsWrite: 'settings.write'});
assert.deepEqual(scopedMethods(`String example = "${compact.replaceAll('"', '\\"')}";`), {});
console.log('PASS route access 9 comments do not create wrappers or hide direct scope checks')

const unsafeWrappers = [
  'public void contentRead(final RequestContext context) { if (enabled) required(context, "content.read"); }',
  'public void contentRead(final RequestContext context) { required(context, "content.read"); extra(context); }',
  'public void contentRead(final RequestContext context) { other(context, "content.read"); }',
  'public void contentRead(final RequestContext context) { required(other, "content.read"); }',
  'public void contentRead(final RequestContext context) { required(context, dynamicScope); }',
  'public void contentRead(final RequestContext context) { required(context, "content.read");'
]
for (const source of unsafeWrappers) {
  assert.deepEqual(scopedMethods(source), {});
  assert.throws(() => access(route, ['"x"', 'p::save', 'auth::contentRead'], scopedMethods(source)), /Unknown auth method contentRead/);
}
console.log('PASS route access 10 conditional, changed and incomplete wrappers stay unclassified')

const formattedScopes = scopedMethods(`${expanded}
public void activityWrite(final RequestContext context) {
    required(context, "activity.write");
}`)
assert.deepEqual(access(route, ['"x"', 'p::save', 'auth::contentRead', 'auth::activityWrite'], formattedScopes).requiredScopes,
  ['content.read', 'activity.write']);
assert.throws(() => access(route, ['"x"', 'p::list', 'auth::optionalProfile'], formattedScopes), /lacks explicit anonymous gate/);
assert.throws(() => access(route, ['"x"', 'p::save', 'auth::unknown'], formattedScopes), /Unknown auth method unknown/);
console.log('PASS route access 11 formatted scopes preserve compound requirements and anonymous gate failures')
