#!/usr/bin/env node
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const home=process.env.JAVA_HOME||'/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const dependencies = [
 process.env.API_V3_JODD_HTTP_JAR || '/tmp/rhythm-jodd-http-5.0.13.jar',
 process.env.API_V3_JODD_CORE_JAR || '/tmp/rhythm-jodd-core-5.0.13.jar',
 process.env.API_V3_MAIL_JAR || '/tmp/rhythm-javax-mail-1.6.2.jar',
 process.env.API_V3_ACTIVATION_JAR || path.join(os.homedir(), '.m2/repository/javax/activation/javax.activation-api/1.2.0/javax.activation-api-1.2.0.jar')
];
for(const file of dependencies)if(!fs.existsSync(file))throw Error('Set the API_V3_*_JAR variables to the pom.xml Jodd/JavaMail/Activation dependencies');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rhythm-real-smtp-')),files=[];
function put(pkg, name, body) {
 const file = path.join(dir, pkg.replaceAll('.', '/'), name + '.java');
 fs.mkdirSync(path.dirname(file), { recursive: true });
 fs.writeFileSync(file, 'package ' + pkg + ';' + body);
 files.push(file);
}

function run(exe, args) {
 const r = spawnSync(exe, args, { stdio: 'inherit' });
 if (r.error) throw r.error;
 if (r.status !== 0) throw Error('SMTP tests failed');
}
try{
const source=fs.readFileSync('src/main/java/org/b3log/symphony/util/Mails.java','utf8');
const values={MAIL_CHANNEL:'local',MAIL_LOCAL_PORT:'25',MAIL_LOCAL_HOST:'fixture.invalid',MAIL_LOCAL_TRANSPORT_PROTOCOL:'smtp',MAIL_LOCAL_ISDEBUG:'true',MAIL_LOCAL_SMTP_AUTH:'false',MAIL_LOCAL_SMTP_SSL:'false',MAIL_LOCAL_SMTP_STARTTLS:'false',MAIL_LOCAL_SMTP_SENDER:'from@example.com',MAIL_LOCAL_SMTP_USERNAME:'fixture',MAIL_LOCAL_SMTP_PASSWORD:'fixture'};
put('org.b3log.symphony.util','Symphonys','public class Symphonys{'+[...new Set([...source.matchAll(/Symphonys\.([A-Z_]+)/g)].map(x=>x[1]))].map(f=>'public static final String '+f+'='+JSON.stringify(values[f]||'')+';').join('')+'}');
put('org.b3log.symphony.model','Common','public class Common{'+[...new Set([...source.matchAll(/Common\.([A-Z_]+)/g)].map(x=>x[1]))].map(f=>'public static final String '+f+'="'+f+'";').join('')+'}');
put('org.b3log.symphony.util','Markdowns','public class Markdowns{}');
put('org.b3log.symphony.util','Templates','public class Templates{public static final freemarker.template.Version FREEMARKER_VER=freemarker.template.Configuration.VERSION_2_3_30;}');
const resource = path.join(dir, 'META-INF/javamail.providers');
fs.mkdirSync(path.dirname(resource), { recursive: true });
fs.writeFileSync(resource, 'protocol=smtp; type=transport; class=org.b3log.symphony.util.AccountSmtpTest$FakeTransport; vendor=fixture; version=1\n');
fs.mkdirSync(path.join(dir,'mail_tpl'));
const cp=['src/main/resources/lib/latke-core.jar',...dependencies].join(path.delimiter);
run(path.join(home,'bin','javac'),['--release','25','-proc:none','-cp',cp,'-d',dir,...files,...['Mails','AccountMailResponses','ApiV3Json','ApiV3Exception'].map(n=>'src/main/java/org/b3log/symphony/util/'+n+'.java'),'src/test/java/org/b3log/symphony/util/AccountSmtpTest.java']);
run(path.join(home,'bin','java'),['-cp',dir+path.delimiter+cp,'org.b3log.symphony.util.AccountSmtpTest']);
} finally {
 fs.rmSync(dir, { recursive: true, force: true });
}
