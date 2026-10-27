# v3 Markdown预览（第六十二轮，2026-10-26）

## 实际修改

后端：

- `src/main/java/org/b3log/symphony/processor/ApiV3MarkdownProcessor.java`：新增POST `/api/v3/markdown/preview`。content.read必需；只接受content字符串，最大1024000个UTF-16单位，保留原文/空白；严格原始UTF8/JSON、未知字段、孤立代理字符、控制字符、MIME和请求体大小边界。重用既有原始字节适配器，避免Latke归一化损坏字符后误接受。
- `src/main/java/org/b3log/symphony/processor/ApiV3Processor.java`：总register接线。
- 仅调用既有`Markdowns.toPrivateHTML`本地Flexmark与Jsoup清洗，不查文章/用户目标、不保存/扣费、不进入公共Markdown缓存或将正文交远程转换。现有类首次初始化探测Lute只发送固定探测文字，不发送本次预览原文。
- 独立有界用户/IP计数及4个并发渲染许可；不消耗共享写限额。429带Retry-After，繁忙503，finally释放许可；同节点保护，不声称跨实例全局限额。
- `src/test/java/org/b3log/symphony/processor/ApiV3MarkdownTest.java`、`tools/api-v3-markdown-test.mjs`：四组真实Dispatcher/auth/原始Netty正文专项，提取既有私有渲染方法和原Parser options并使用真实Flexmark/Jsoup；Token持久化、响应信封、Emotions目录为适配器。
- `API.md`、`UPDATE_LOG.md`、`docs/api-v3-progress.md`、`docs/api-route-inventory.md`、`AGENTS.md`：公开契约、更新记录及内部边界。注册可达282=GET139+POST72+PUT46+DELETE24+WS1；全仓扫描829为另一口径。

客户端 `/Users/ling/Documents/fishpi/website_v3`：

- `src/api/v3-markdown.ts`：类型/字符边界、原文POST、no-store与信号、只取html。
- `src/utils/markdownPreview.ts`：Vditor同步transform返回空，屏蔽本地HTML回退；parse在预览容器中异步写服务端清洗HTML。正文变化清旧结果、取消请求，序号+身份版本+当前正文复核；错误只作textContent，显式按钮重试。不缓存正文、不持久化HTML。
- `src/components/MarkdownEditor.vue`：移除旧`/markdown`，共享桌面/移动工具栏接回调；input/外部setValue清旧预览，身份变化清除，普通token轮换保持，卸载取消监听及请求。其他旧upload/@/内置emoji尚待迁移。
- `tools/v3-markdown-test.mjs`：七组真实TS/API/适配器与轻量DOM专项；组件接线断言不是完整Vditor自动化。
- `AGENTS.md`、`docs/V3_MIGRATION.md`：迁移状态；构建重新生成dist/client与dist/server，没直接改CSS/min.js。

## 已执行证据

- `node tools/api-v3-markdown-test.mjs`：4组通过，`/tmp/rhythm-v3-markdown-tests.log`。覆盖未鉴权/错误scope/旧Cookie与apiKey不提升，415/400/413/422，畸形原UTF8、重复JSON键、空值、XSS脚本/事件/javascript/iframe清洗、真实中文emoji/粗体/表格、no-store、预算与并发拒绝及恢复。
- Runner只做Java25定向javac，不运行Maven/应用IoC。依赖位置：Android Studio jbr/jsoup/caffeine及捆绑latke；`API_V3_MARKDOWN_JARS`目录需项目pom同版本Flexmark0.50.40的core/util/formatter/ext-autolink/ext-gfm-strikethrough/ext-gfm-tasklist/ext-tables与autolink0.6.0。此轮由Maven Central下载至`/tmp/rhythm-v3-markdown-jars`，未修改项目依赖。
- 客户端`node tools/v3-markdown-test.mjs`：7组通过，`/tmp/rhythm-v3-markdown-client-tests.log`；文章/草稿27组、评论17组回归通过，对应`/tmp/rhythm-v3-markdown-{posts,comments}-regression.log`。
- `corepack yarn run build`类型检查、client/server构建通过，`/tmp/rhythm-v3-markdown-build.log`。
- 71个生成JS版本号命中0，`/tmp/rhythm-v3-markdown-js-audit.json`；dist/client无map/sourceMappingURL/sourceURL；两仓git diff --check通过。
- IAB真实CDN Vditor+Express/Vite+合成后端：`/post`恢复合成身份，输入中文emoji，更多→预览，实际DOM `.vditor-preview .vditor-reset`返回合成HTML，后端观测POST且Authorization存在。再输入FAIL_PREVIEW触发503，重试按钮可见且点击重发；不调用文章发布/保存。这里只证明异步回调与真实Vditor对接，合成后端只转义文字，不替代Java渲染验证。默认窄工具栏，未完成显式桌面/移动两个视口及视觉截图验收。

## 安全与反漏扫复核

- 新入口明确要求Bearer/content.read，Cookie/query/body apiKey不作身份；Router仍先全局BeforeRequestHandler。未扩anonymous.viewSkips，未改变/test/验证码解封链路。实际黑名单/每五次验证码/开发风控开关未运行验收。
- 只有调用者提交的文字，无目标ID/SQL/Repository写入，因此无文章越权或SQL注入路径；无Cookie写授权，无凭据Cookie降级。无持久副作用，不要求幂等键。
- XSS由既有私有Jsoup白名单清洗；客户端错误仅文本。用户URL不在服务端抓取，无新增上传/下载路径、CDN URL或可枚举静态目录。远程图片仍可能被浏览器加载，遵循既有渲染白名单。
- 独立有界计数和渲染并发防滥用；不保证多个实例全局总量。日志不记录原文；异常消息不回显正文。响应只有html且no-store。
- CSS/代码高亮、公式/图表等Vditor后处理在异步HTML插入后不会自动再跑，不能宣称与已发布文章或本地Lute排版完全一致；API文档明确格式支持差异，后续按真实联调结果补齐。

## 完整人工链路与未验项

Java已改，先通过IDEA重新编译或重启服务端；前端build后强刷。不要拿未编译服务的404判断源码失败。

1. 登录v3，打开`/post`；分别桌面、移动工具栏输入标题与含中文emoji、标题/粗体/表格/代码的Markdown，打开分屏/预览。确认POST新路径携带当前Bearer，显示清洗HTML、不触发发布或扣费、不访问旧`/markdown`。
2. 连续修改正文，确认旧输出清除、最终只显示当前文本；切换账号/退出/离开发帖页时，延迟旧响应不得带回私有正文。正常token刷新应继续允许当前身份预览。
3. 制造预览503/断网，确认错误纯文本且原编辑内容保留；恢复后点击重试，只重发预览，不发文章/草稿写请求。再清空正文，预览应清空。
4. 提交HTML脚本/事件/javascript链接，确认不执行；未登录/失效Bearer401、缺content.read403、错误MIME415、非法UTF8/JSON400、控制/超长正文422。查看请求/日志不应暴露其他账号资料。
5. 真实环境回归黑名单→/test→validateCaptcha解封、匿名访问门禁及开发关闭风控；复核PC/移动工具栏、字体/代码/公式显示。完整IoC启动、双视口视觉、真实令牌撤销、全局风控仍未验收。

上传/@/内置表情/标签联想/专栏选择、文章删除/置顶客户端、匿名本人管理、私信/聊天室主体及其他总清单继续。全v3目标未完成；本轮无SQL/FTL/APP/Maven/提交或推送。发布时同步API.md到网站API文档文章。
