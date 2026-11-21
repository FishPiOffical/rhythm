# v3 标签联想（第六十七轮，2026-10-26）

## 已确认范围与实际文件

按用户确认：content.read；trim后忽略大小写前缀，最多64个UTF-16单位，现有标签字符范围；只返回仍有关联文章的有效标签名称，最多7条，空前缀热门。选择只替换当前标签，不替用户新增/截断其他标签。

后端：

- `src/main/java/org/b3log/symphony/service/ApiV3TagSuggestionQueryService.java`：复用Tag.TAG_TITLE_PATTERN，统一浏览器trim的外层空白集合；当前actor有效，标签status=0/引用数>0，EXISTS关联表JOIN文章主表确保不只依赖残留计数。前缀参数化并字面转义下划线，按引用数/名称/ID排序最多7，只投影名称。坏源行/重复标题/数据库失败503，不假装空结果。无HTTP指定owner，不用旧缓存。
- `src/main/java/org/b3log/symphony/processor/ApiV3TagSuggestionProcessor.java`：GET `/api/v3/suggestions/tags`，content.read；原始query严格解析，独立有界预算/并发控制，no-store。`ApiV3Processor.java`总注册接线。
- `src/test/java/org/b3log/symphony/service/ApiV3TagSuggestionsTest.java`、`tools/api-v3-tag-suggestions-test.mjs`：实际SQL/HTTP专项，SQLite与独立临时MySQL；Tag字符Pattern由生产常量提取，Repository/Token持久化/响应信封为适配器。无新增表/Repository/写操作，无迁移或repository.json变更。
- `API.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-route-inventory.md`、`AGENTS.md`及本记录。注册284=GET141+POST72+PUT46+DELETE24+WS1，HTTP283；全仓831是另一口径。

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-tag-suggestions.ts`：严格64/字符范围与返回最多7个唯一字符串，检查前缀对应、长度、类型，保留名称本身；空前缀也请求热门，无假目录兜底。
- `src/utils/tagInput.ts`：按既有文章分隔符逗号/中文逗号/顿号/中英文分号定位光标token；不跨多个token选择替换，保留所有其他字符及首尾空白，不自动去重/补逗号/截断。联想前缀取光标前当前token，选中后替换当前整个token的非空白部分。
- `src/components/posts/TagInput.vue`：受控字段、200ms debounce、焦点/失焦、IME、箭头/Enter/Escape、ARIA建议列表，错误可继续手输。query序号/AbortSignal/身份版本/当前整串快照检查，光标改变拒旧选择；外部重设、换人、退出、卸载清在途与菜单。选择只emit原输入局部替换，并恢复正确光标。SCSS源码，无嵌套卡片。
- `src/views/PostView.vue`：普通文章标签字段改TagInput v-model；长文原无该字段保持，提交/草稿仍走既有适配器，不新增目录或写请求。
- `tools/v3-tag-suggestions-test.mjs`：9组生产API/工具函数/完整Vue+Pinia+轻量原生输入适配器；真实计时器，验证debounce/异步/IME/键盘/当前token/空白/分隔符/不截断与父回填。
- `tools/v3-posts-test.mjs`：适配受控TagInput组件，并新增第29组完整PostView→冻结命令→真实写API，确认`已有,C++;保留`原样进入发布请求。组件内选择逻辑由独立完整组件专项覆盖。
- `AGENTS.md`、`docs/V3_MIGRATION.md`及build生成dist/client/server。无APP/Maven/提交/推送。

## 执行证据

- Java25定向javac+SQLite四组通过：`/tmp/rhythm-v3-tag-suggestions-tests.log`。
- 同四组临时MySQL8.4通过，finally已移除容器：`/tmp/rhythm-v3-tag-suggestions-mysql.log`。
- 覆盖热门7、英文大小写/中文/C++/字面下划线、无引用/停用标签、残留计数但关联文章已不存在、当前actor、64/65边界/Unicode外层空白/非法控制字符、失效Bearer/缺scope/旧Cookie/apiKey不提升、query重复键/坏UTF8/标点编码、数据库失败503、配额与并发拒绝后恢复；SELECT适配器禁止写SQL。
- 客户端9组：`/tmp/rhythm-v3-tag-suggestions-client-tests.log`。
- 文章/草稿29组：`/tmp/rhythm-v3-tag-suggestions-posts.log`；评论17、本人专栏7、用户联想7、编辑器表情7回归：`/tmp/rhythm-v3-tag-suggestions-v3-*-test.log`。
- 类型/client/server构建通过：`/tmp/rhythm-v3-tag-suggestions-build.log`。71个生成JS版本0/无map/sourceMappingURL/sourceURL，报告`/tmp/rhythm-v3-tag-suggestions-js-audit.json`；两仓diff --check。
- IAB实际PostView/TagInput+合成HTTP：输入`已有,C+;保留`，Home/右移到中间token，选C++后DOM值为`已有,C++;保留`、光标6；存草稿合成后端记录同一完整字符串；再以ArrowDown/Enter选Java。390×844移动输入`已保留,中`，点击中文后`已保留,中文`、光标6，输入宽326不越界；截图`/tmp/rhythm-v3-tag-suggestions-mobile-proof.png`。
- 合成草稿500只证明客户端提交字段，不证明真实保存。真实Java未通过IDEA编译/启动，完整IoC/生产数据/权限/风控尚待验收。

## 安全与边界

- 新GET明确Bearer/content.read，未改anonymous.viewSkips、BeforeRequestHandler、test/validateCaptcha；不是匿名入口。真实黑名单、首访/周期验证码、开发关闭风控、凭据epoch撤销未运行验收。
- actor由认证上下文取得，query只准prefix，无body/query apiKey提升。只返标签名，不返关联文章ID/正文/编辑者/计数或私密账号资料；标签目录语义与现有公共标签目录一致，不声称按当前读者文章可见性重算热门。
- 参数化SQL/现有字符表/字面下划线，表名仅来自已有Repository；无服务器抓取URL/SSRF/上传/下载、SQL写或CSRF状态变化。无新CDN资源或可枚举目录，版本/map检查覆盖本轮产物，未替代全工作区发布前审计。
- 新增EXISTS校验实际关联，热度仍取已有tagReferenceCount排序；不在读取时修复计数。LOWER和关联查询的真实生产执行计划/压力未验，独立预算只同节点，不宣称全局限额。
- Vue文本渲染标签与错误；选择来源是已验证本次结果和当前token快照，迟到/旧按钮不能改另一个位置。搜索失败保留手工输入，不伪造成功、热门或空目录。
- 客户端仅不改其他输入；文章提交的既有规范化/数量/长度/审核规则未改，不能据本轮选择器承诺服务端不规范化标签。

## 完整人工验收

Java有改动，联调前先IDEA重新编译/重启，再前端build/强刷。未编译服务的404不能当源码缺失；无Maven。

1. 登录→普通发帖或本人文章编辑→聚焦空标签，应取热门；输入大小写前缀、中文、C+/#等并选择，检查只替换光标当前token，其他标签/空白/分隔符原样保留。保存草稿并回读，再发布确认最终规范化结果符合原规则。
2. 光标放中间标签，选择后尾部标签不丢；选区跨多个标签时不替换。超过4个标签或输入超限仍保留原输入，让既有提交验证提示，不能客户端悄悄截断。
3. 方向键/Enter选择而不误提交表单，Escape/Tab/失焦关闭；真实中文输入法组合过程不查半成品、不抢Enter，组合结束才联想。桌面/移动/触屏和键盘焦点分别验，真实OS输入法未由合成事件代替。
4. 快速输入/移动光标、换账号/退出/换草稿/离开页面，恢复旧响应不回填；普通token轮换可完成当前读。断网503保留手输，重新聚焦或输入可重试；缺content.read/失效Bearer与query边界按协议拒绝。
5. 真实标签停用/移除关联/文章物理删除后不出现在建议；空前缀前7及排序、生产SQL计划、全局黑名单→test→解封和开发关闭风控仍需真实服务验收。

本轮新增功能完成源码与定向验证，全v3目标仍未完成；文章删除/置顶客户端、匿名本人管理、私信/聊天室主体等及全链运行验收继续。
