import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

const home = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
const lang = process.env.COMMONS_LANG3_JAR || path.join(os.homedir(), '.m2/repository/org/apache/commons/commons-lang3/3.1/commons-lang3-3.1.jar')
const cp = ['src/main/resources/lib/latke-core.jar', lang].map(p => path.resolve(p)).join(path.delimiter)
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-newcomer-role-'))
function write(file, body) { const target = path.join(work, file); fs.mkdirSync(path.dirname(target), { recursive: true }); fs.writeFileSync(target, body); return target }
function run(command, args) { const result = spawnSync(command, args, { stdio: 'inherit' }); if (result.error) throw result.error; if (result.status !== 0) throw Error('Newcomer event tests failed') }
try {
  // Only persistence boundaries are adapted; both production listeners and model constants are compiled unchanged.
  const roles = write('RoleRepository.java', `package org.b3log.symphony.repository;
import org.json.JSONObject;import org.b3log.latke.repository.RepositoryException;
public class RoleRepository {public JSONObject row;public boolean fail;public int reads;public String lastId;
public JSONObject get(String id)throws RepositoryException{reads++;lastId=id;if(fail)throw new RepositoryException("fixture role read failure");return row;}}`)
  const users = write('UserMgmtService.java', `package org.b3log.symphony.service;
import org.json.JSONObject;import org.b3log.latke.service.ServiceException;
public class UserMgmtService {public int writes;public String id;public JSONObject value;
public void updateUser(String id,JSONObject value)throws ServiceException{writes++;this.id=id;this.value=new JSONObject(value.toString());}}`)
  const test = write('NewcomerRoleEventsTest.java', `import org.b3log.latke.event.*;import org.b3log.latke.model.User;import org.b3log.symphony.model.*;import org.b3log.symphony.event.*;import org.b3log.symphony.repository.RoleRepository;import org.b3log.symphony.service.UserMgmtService;import org.json.JSONObject;
public class NewcomerRoleEventsTest {
static void require(boolean condition){if(!condition)throw new AssertionError();}
static void inject(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
static void scenario(boolean update,String tags,String role,boolean exists,boolean fail,boolean promoted,boolean queried)throws Exception{
AbstractEventListener<JSONObject> listener=update?new ChangeRoleUpdateHandler():new ChangeRoleHandler();
var roles=new RoleRepository();roles.row=exists?new JSONObject().put("oId",Role.ROLE_ID_C_INTRODUCTION_MEMBER):null;roles.fail=fail;
var users=new UserMgmtService();inject(listener,"roleRepository",roles);inject(listener,"userMgmtService",users);
var author=new JSONObject().put("oId","123").put(User.USER_ROLE,role).put("userPoint",81);
listener.action(new Event<JSONObject>(listener.getEventType(),new JSONObject().put(Article.ARTICLE_TAGS,tags).put("author",author)));
require(users.writes==(promoted?1:0));require(roles.reads==(queried?1:0));
require(author.getString(User.USER_ROLE).equals(promoted?Role.ROLE_ID_C_INTRODUCTION_MEMBER:role));require(author.getInt("userPoint")==81);
if(queried)require(Role.ROLE_ID_C_INTRODUCTION_MEMBER.equals(roles.lastId));
if(promoted){require(users.id.equals("123"));require(users.value.getString(User.USER_ROLE).equals(Role.ROLE_ID_C_INTRODUCTION_MEMBER));}
}
public static void main(String[] args)throws Exception{for(boolean update:new boolean[]{false,true}){
String kind=update?"update":"publication";
scenario(update,"新人报道,其他",Role.ROLE_ID_C_DEFAULT,false,false,false,true);System.out.println("PASS "+kind+" missing role preserves default and never writes");
scenario(update,"新人报道",Role.ROLE_ID_C_DEFAULT,true,false,true,true);System.out.println("PASS "+kind+" existing target retains original promotion and update boundary");
scenario(update,"新人报到",Role.ROLE_ID_C_DEFAULT,true,false,true,true);System.out.println("PASS "+kind+" alternate introduction tag retains promotion");
scenario(update,"新人报道",Role.ROLE_ID_C_DEFAULT,false,true,false,true);System.out.println("PASS "+kind+" lookup failure preserves role without writes");
scenario(update,"新人报道",Role.ROLE_ID_C_MEMBER,true,false,false,false);System.out.println("PASS "+kind+" existing non-default role remains untouched");
scenario(update,"普通标签",Role.ROLE_ID_C_DEFAULT,true,false,false,false);System.out.println("PASS "+kind+" unrelated article does not query or update roles");
}}}`)
  const source = 'src/main/java/org/b3log/symphony/'
  const real = ['model/Role', 'model/Article', 'event/EventTypes', 'event/ChangeRoleHandler', 'event/ChangeRoleUpdateHandler'].map(p => source + p + '.java')
  run(path.join(home, 'bin/javac'), ['--release', '25', '-cp', cp, '-d', path.join(work, 'classes'), roles, users, test, ...real])
  run(path.join(home, 'bin/java'), ['-cp', path.join(work, 'classes') + path.delimiter + cp, 'NewcomerRoleEventsTest'])
} finally { fs.rmSync(work, { recursive: true, force: true }) }
