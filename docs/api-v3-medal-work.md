# v3 用户勋章读取实现记录

内部实现/测试交接，2026-10-20；不是公开API文档。本人及公开展示两条读取已完成；本轮不开放所有定义目录，也不做勋章排序/显示写入。

## 实际入口与契约

| GET接口 | 认证/身份 | data |
| --- | --- | --- |
| `/api/v3/me/medals` | profile.read Bearer，身份只来自ApiV3Processor.IDENTITY；查询当前状态0本人 | 标准list/pagination，包含本人当前有效、已显示及隐藏的勋章 |
| `/api/v3/users/{userName}/medals` | 可匿名；若带Bearer则需profile.read；显式挂AnonymousViewCheckMidware | 标准list/pagination，仅目标用户当前display=true且有效的勋章 |

分页沿用 `p` 默认1、`size` 默认20/最大50，真实SQL count和LIMIT/OFFSET；按display_order、持有行oId、定义行oId稳定升序。目标用户名沿用v3公开主页同样的ASCII字母/数字/下划线/短横线规则、长度1-64。无此用户或不可展示账号404，无勋章用户200空列表。没有按输入userId代查本人的能力。

list项字段：

- `id`：medal.medal_id，业务勋章ID；0也是合法勋章ID。不是medal表oId或user_medal持有行ID。
- `name` / `type` / `description`：纯文本。描述沿用现有 `{var1}`、`{var2}` 对应持有data分号序列替换，但不下发原data。
- `order`：原display_order。
- `expiresAt`：毫秒时间戳，0永久；本人保留负值存量永久语义。
- `displayed`：仅本人返回，布尔值；公开读取始终只筛选显示项，因此不额外输出此字段。

例：`{"list":[{"id":"0","name":"初见","type":"稀有","description":"欢迎 Alice","order":0,"expiresAt":0,"displayed":true}],"pagination":{"p":1,"size":20,"total":1,"pageCount":1}}`；公开项移除displayed。

无头像、URL或图片HTML返回；也不下发raw medal_attr任意JSON/CSS。当前旧图像生成`/gen`是另一条认证链，不擅自把它当作可直接Bearer获取的v3图像端点。展示图片/风格的v3契约可另定。

## 保留的旧规则与读取副作用隔离

- 旧 `MedalService.getUserMedals/getUserDisplayedValidMedals` 在读取时会删除过期user_medal，且异常时可能返回空列表；v3直接参数化SELECT，不调用上述方法，不删除/迁移/提交任何记录，RepositoryException统一503。
- 旧本人方法把expire_time<=0视永久；旧公开展示方法只把expire_time=0或未来视有效。本轮分别保持这两种语义，有负值专项测试，不悄悄统一规则。
- 旧主页UserCheck拒绝未验证/失效用户，同时现有体系中状态0（正常）和3（禁止登录）用户仍可展示；新公开读取允许0/3，拒绝1/2/4及未知状态。本人只允许0，与v3 Token认证一致。
- 查询的count与page重带相同目标身份、当前状态、display与expiry谓词。测试模拟count之后改隐藏或停用用户，data不泄露。没有承诺跨两条SELECT的一致快照；并发修改可能让分页total与list短暂不一致。
- 丢失勋章定义的悬空持有不出现在list或total中；本人隐藏持有可见，公开展示不可见。没有公开授予名单、持有人总数或隐藏勋章定义目录。

## 实际新增文件

1. `src/main/java/org/b3log/symphony/processor/ApiV3MedalProcessor.java`：两条GET；本人profileRead，公开optionalProfile + anonymous.handle。
2. `src/main/java/org/b3log/symphony/service/ApiV3MedalQueryService.java`：参数化只读、当前账号、真实分页与反复授权谓词、503故障边界。
3. `src/main/java/org/b3log/symphony/util/ApiV3MedalDto.java`：固定DTO、纯文本、受限变量替换、私有持有字段不外传。
4. `src/test/java/org/b3log/symphony/service/ApiV3MedalQueryTest.java`：隔离SQLite/实际路由/鉴权/匿名盾专项。
5. `tools/api-v3-medal-test.mjs`：Java25隔离runner。
6. `docs/api-v3-medal-work.md`：本记录。

没有改旧方法、旧路由、PC/mobile模板、JS/SCSS/生成产物、数据库表或Repository注册。Java改动联调前须用户通过IDEA重新编译或重启服务端；未确认前不能用旧8080响应否认实现。

主代理整合建议：`ApiV3Processor.register()` 中增加 `ApiV3MedalProcessor.register();`。API.md/UPDATE_LOG/AGENTS/进度清单由主代理统一维护。AGENTS关键链路可加：v3勋章读取只读投影，禁止调用会删除过期持有的旧read；本人含隐藏、公开只display有效，拒绝裸传attr/data。

## 已执行验证

运行 `node tools/api-v3-medal-test.mjs`，Java25隔离编译和临时SQLite，**4组PASS**：

1. 实际prefixed SQL：本人/他人、隐藏边界、到期/未来/0/负值、悬空定义、状态0/3与1/2/4、空用户、分页/稳定排序、有界入参、count之后隐藏/停用也不泄露；读取前后持有条数不变。
2. 实际Dispatcher路由、HTTP控制器和ApiV3AuthMidware：缺Bearer401、错误Bearer401、错scope403、匿名公开成功；Cookie/apiKey/userId不提供本人身份；body字段白名单、no-store/Vary、分页422、非法用户名422、503及无驱动错误返回。
3. **实际AnonymousViewCheckMidware源码编译执行**：新IP首次访问和第五次访问在SQL前触发/test并加入黑名单；有效Bearer不增加匿名计数。Sessions.getUser与旧ApiProcessor.getUserByKey在测试设为throw，证明v3路径不回退它们。验证实际注册的中间件链，而不是仅用文字扫描宣称经过匿名盾。
4. 描述的受限变量替换、HTML/脚本投影为纯文本、超长名称/替换膨胀的响应边界、raw/畸形medal_attr完全不选择不下发；count/page分别故障时503。

测试适配：UserRepository/Token/站点Option/爬虫查询/网络响应传输为隔离适配；medal和user_medal Repository、v3查询/DTO/控制器/auth、Latke Dispatcher及匿名盾逻辑均实际编译。Response.sendRedirect适配仅记录Location/302而不写真实Socket。测试使用本地Caffeine cache，无DNS/防火墙真实操作、无运行中服务。编译仅现有UserExt弃用注解及Netty/SQLite运行时警告，无失败。

## 安全与反漏扫检查

- 未鉴权/越权：本人路由显式Bearer+profile.read，目标不来自Cookie、apiKey或请求userId；公开路由显式optionalProfile及匿名盾。所有公开持有输出均受display/有效期和目标状态条件限制。
- CSRF：本轮全部GET，无写入、无隐藏的过期删除，不新增Cookie/apiKey写接口。
- XSS：DTO输出纯文本，原HTML和任意样式JSON不下发，客户端应按文本显示；不提供可执行HTML字段。
- SQL注入：身份/状态/时间/分页全绑定；动态列名仅owner内部二选一，表名仅Repository#getName。
- SSRF：无远程获取，无头像/URL输入或回传，不新增图片代理。
- 批量滥用：分页size最大50，有界偏移；没有新增会改变旧业务规则的频控。极限并发/实际MySQL索引性能未实测。
- 敏感信息：未select账号敏感列和medal_attr；raw data仅参与受限描述渲染，不原样下发，不返回user_id/user_medal_oId/其他后台列。
- 匿名链路：新公开路由实际验证匿名盾首次/第五次门禁；未更改anonymous.viewSkips，无新例外。全局BeforeRequestHandler黑名单跳转和CaptchaProcessor#validateCaptcha解封属于整轮待验证项，未用局部测试替代。
- 静态/CDN版本号/sourceMappingURL/目录枚举/上传下载路径：本轮没有新增资源URL、静态目录、source map或上传下载能力；扫描本模块仅新增Java/测试/内部文档。整轮全仓CDN和历史目录问题需主代理发布审查，不能声称全仓通过。

未执行Maven、全应用编译、真实MySQL、真实OAuth签发/撤销、8080浏览器、生产请求、BeforeRequestHandler全链与验证码真实解封；没有git提交。本专项不能替代生产或完整应用联调。

## 完整人工测试链路

**联调前先手动通过IDEA重新编译或重启服务端。**

1. 使用现有管理员勋章入口给测试用户A授予“永久显示”“永久隐藏”“未来到期显示”“已到期显示”四种持有；用户B拥有另一勋章。通过PC/mobile旧主页与本人勋章面板记录预期显示及排序。无需新表迁移，但仍须满足既有v3 Token基础迁移前提。
2. 用A的profile.read Bearer请求GET `/api/v3/me/medals`：应显示本人有效含隐藏，排除过期，字段只有约定7项；raw data/attr/user_id/持有行ID不出现。换B凭据只能读B；Cookie、apiKey或`userId=B`不能覆盖A。
3. 通过公开GET `/api/v3/users/A用户名/medals`：应与旧PC/mobile公开主页展示集合一致，排除隐藏/过期，无displayed和私有字段；空用户列表为空、未知/未验证/失效/永久停用用户404。状态3禁止登录用户公开展示沿旧语义保留，不能据此获得本人Bearer。
4. `p=1&size=1`、第二页、越过末页检查稳定顺序和total/pageCount；`size=51`、`p=0`、非数字分页、非法/过长用户名422。缺Bearer本人401、无效Bearer401、缺profile.read403；公开不带Bearer可进入匿名盾，显式坏凭据不能回退匿名成功。
5. 风控启用的开发配置以新IP匿名访问公开接口，首次/test；通过验证码后返回再每5次验证；黑名单IP必须被BeforeRequestHandler拦到/test，`/validateCaptcha`应正确解封。Cookie/旧apiKey不应被新v3资源当作Bearer身份。开发模式通常关闭风控，需显式启用后再测，不能把默认开发放行当生产已通过。
6. 读取前后检查已到期持有记录仍存在；v3 GET不得清理。隐藏/到期修改后再次读公开接口应立即看不到；读count/data之间并发隐藏/停用时不能返回旧可见持有内容。
7. 测试描述变量、HTML、损坏样式JSON、超长脏值：正常变量替换为纯文本，脚本/标签不变成HTML输出；损坏attr不导致裸返整行/500或泄露JSON。读故障返回503而非“成功空列表”。检查no-store及Vary:Authorization。
8. 回归旧PC/mobile公开主页和本人勋章管理显示/排序/过期行为；本轮不改其接口。对整轮新入口继续检查匿名/黑名单/验证码、anonymous.viewSkips、公开API鉴权及静态/CDN/sourceMappingURL/目录枚举/上传下载路径。无法验证的环节由最终回复明列。

## 留待后续的能力

- 不新增所有定义/聚合勋章目录：现有getAllMedals用于管理，没有明确公开全定义需求；不能借“目录”扩大后台定义可见范围。
- 不改显示/排序：旧排序是up/down相邻交换，重试可能重复移动，缺少根事务内用户/持有锁；不能直接套幂等包装复用自提交旧方法。后续应先设计当前事务核心，再与settings.write/Bearer/幂等组合；显示操作虽然字段规则清楚，也需统一锁和只更新相关字段，避免整行覆盖。
- 不返raw样式/渲染器配置：若客户端需要勋章图片，应单独定义v3图片地址/读取鉴权与缓存契约，不能未经评审输出任意CSS/JSON或把旧`/gen`当公开Bearer资源。
