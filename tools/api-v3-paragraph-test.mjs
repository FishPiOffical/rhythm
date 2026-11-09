#!/usr/bin/env node
/** 执行实际段落解析、标注和本地 Flexmark 转换，持久层隔离。
 * 清洗、表情与媒体使用受控适配器，完整生产 Markdown 链路需另行验收。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const ide = '/Applications/Android Studio.app/Contents', home = process.env.JAVA_HOME || `${ide}/jbr/Contents/Home`;
const testng = process.env.API_V3_TESTNG_JAR || `${ide}/plugins/testng/lib/testng.jar`;
const jsoup = process.env.API_V3_JSOUP_JAR || `${ide}/lib/intellij.libraries.jsoup.jar`;
const latke = 'src/main/resources/lib/latke-core.jar';
const sqlite = process.env.API_V3_SQLITE_JAR || `${ide}/plugins/android/lib/sqlite-jdbc-3.51.1.0.jar`;
for (const file of [testng, jsoup, latke, sqlite]) if (!fs.existsSync(file)) throw new Error('Configure Java 25, TestNG and Jsoup');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-v3-paragraph-test-'));
function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error('Paragraph annotation tests failed');
}
try {
  const shims = {
    'org/b3log/symphony/repository/CommentRepository.java': 'package org.b3log.symphony.repository; public class CommentRepository extends org.b3log.latke.repository.AbstractRepository { public CommentRepository() {super("comment");} }',
    'org/b3log/symphony/repository/ArticleRepository.java': 'package org.b3log.symphony.repository; public class ArticleRepository extends org.b3log.latke.repository.AbstractRepository { public ArticleRepository() {super("article");} }',
    'org/b3log/symphony/util/Markdowns.java': String.raw`package org.b3log.symphony.util; public class Markdowns {
      private static final com.vladsch.flexmark.util.data.DataHolder OPTIONS = new com.vladsch.flexmark.util.data.MutableDataSet()
        .set(com.vladsch.flexmark.parser.Parser.EXTENSIONS,java.util.Arrays.asList(com.vladsch.flexmark.ext.tables.TablesExtension.create(),com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension.create(),com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension.create(),com.vladsch.flexmark.ext.autolink.AutolinkExtension.create()))
        .set(com.vladsch.flexmark.html.HtmlRenderer.SOFT_BREAK,"<br />\n");
      private static final com.vladsch.flexmark.parser.Parser PARSER=com.vladsch.flexmark.parser.Parser.builder(OPTIONS).build();
      private static final com.vladsch.flexmark.html.HtmlRenderer RENDERER=com.vladsch.flexmark.html.HtmlRenderer.builder(OPTIONS).build();
      public static String toHTML(String text){return RENDERER.render(PARSER.parse(text));}
      public static String clean(String text,String base){return text;}
    }`,
    'org/b3log/symphony/util/Emotions.java': 'package org.b3log.symphony.util; public class Emotions {public static String convert(String text){return text;} }',
    'org/b3log/symphony/util/MediaPlayers.java': 'package org.b3log.symphony.util; public class MediaPlayers {public static String renderAudio(String text){return text;} public static String renderVideo(String text){return text;} }'
  };
  const files = [];
  for (const [name, source] of Object.entries(shims)) {
    const file = path.join(work, name);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, source);
    files.push(file);
  }
  const repository=path.join(os.homedir(),'.m2/repository');
  const jars=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?jars(path.join(dir,e.name)):/\.jar$/.test(e.name)&&!/(sources|javadoc)\.jar$/.test(e.name)?[path.join(dir,e.name)]:[]);
  const flexmarkVersion=fs.readFileSync('pom.xml','utf8').match(/<flexmark.version>([^<]+)<\/flexmark.version>/)?.[1];
  if(!flexmarkVersion)throw Error('Missing project Flexmark version');
  const markdownJars=[...jars(path.join(repository,'com/vladsch/flexmark')).filter(file=>file.includes(path.sep+flexmarkVersion+path.sep)),...jars(path.join(repository,'org/nibor/autolink'))];
  const cp = [latke, testng, jsoup, sqlite,...markdownJars].join(path.delimiter), classes = path.join(work, 'classes');
  run(path.join(home, 'bin', 'javac'), ['--release', '25', '-proc:none', '-cp', cp, '-d', classes, ...files,
    'src/main/java/org/b3log/symphony/model/Comment.java', 'src/main/java/org/b3log/symphony/model/Article.java',
    'src/main/java/org/b3log/symphony/service/ShortLinkQueryService.java', 'src/main/java/org/b3log/symphony/util/ArticlePublicationCaches.java', 'src/main/java/org/b3log/symphony/util/ApiV3Exception.java',
    'src/main/java/org/b3log/symphony/service/LongArticleParagraphService.java',
    'src/test/java/org/b3log/symphony/service/LongArticleParagraphAnnotationTest.java',
    'src/test/java/org/b3log/symphony/service/LongArticleParagraphMigrationTest.java']);
  run(path.join(home, 'bin', 'java'), ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.service.LongArticleParagraphAnnotationTest']);
  run(path.join(home, 'bin', 'java'), ['-cp', [classes, cp].join(path.delimiter), 'org.b3log.symphony.service.LongArticleParagraphMigrationTest']);
} finally {
  fs.rmSync(work, { recursive: true, force: true });
}
