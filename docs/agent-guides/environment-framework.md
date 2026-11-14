# 开发环境与 Latke 框架

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[历史恢复记录](../local-idea-environment.md)、[环境变更说明](history.md)、[登录凭据](auth-account.md)、[游戏存档](frontend-games.md)。

## 当前环境与历史配置

- 当前开发环境为 `http://localhost:8080`，生产环境为摸鱼派 `https://fishpi.cn`（用户于2026-11-13确认）。可用 Chrome 辅助诊断；JS/SCSS 构建后强制刷新。Java/FTL 联调前须明确提醒用户通过 IDEA 重新编译或重启，确认前不以旧页面行为判断改动无效。本地服务经常读取 `target/classes`，资源未更新时优先同步对应资源或提醒重新编译。
- 在 WSL 环境中不要依赖 git 状态/差异判断工作区是否干净（可能存在异常噪声）；默认按任务目标直接改指定文件，git 清理由用户在 Windows 环境统一处理
- 当前开发地址按用户2026-11-13确认使用8080；旧 `Rhythm Local` 的8081、独立 MySQL容器/3319、IPv4/IPv6分流与临时target仅属历史恢复记录，见 `history.md`、`../local-idea-environment.md`，不得据此覆盖当前环境。向服务发送凭据前核对监听进程及无凭据响应；不停止其他项目。私有配置保持忽略，历史路径 `.idea/rhythm-local/` 由 `LATKE_LOCAL_PROPS` 加载。自动建表不替代正式 SQL 迁移（生成列、索引、种子）；Docker mysql 显式 `--default-character-set=utf8mb4`，读取正文/JSON 用 `--raw` 或 HEX 传输，防止字符集及批量反斜杠转义损坏内容。

## 目录与框架来源

- 后端：`src/main/java/org/b3log/symphony`（入口 `Server.java`，核心分层 `processor/service/repository/model/util`）。
- 资源：`src/main/resources`（配置、静态资源、`skins` 模板）。
- 前端编译输出：`src/main/resources/css`、`src/main/resources/js`。
- 构建：后端 Maven（`pom.xml`），前端 Gulp（`gulpfile.js`、`package.json`）。
- 本地 Latke 框架源码：`C:\Users\陈辉\IdeaProjects\rhy-latke`（非官方版本，分析框架行为优先对照这里，不要假设与官方仓库一致）。

## 事务与 IoC 陷阱

- `ChatroomProcessor` 到 `ApiV3ChatRedPacketService` 必须请求时获取，不能字段注入：红包服务发布消息又依赖该处理器，Latke 启动会递归溢出。常规编译检查使用 `-Dlicense.skip=true`，避免既有 license:format 批量改源码头。
- `ApiV3TransactionSavepoint`嵌套primitive须检测原owner仍active且TX未换；内层回滚保存点失败已中止根时，外层不可再次碰已关闭连接或覆盖原错。
- 当前Latke JDBC `commit()`会吞掉提交异常；v3幂等/Token/Socket根事务用`ApiV3Transactions.strict`提交，primitive不能绕回原提交。旧注解事务未全局替换，不能仅凭事务结束判定成功；升级jar需重跑提交故障专项。WS回调和后台worker必须显式释放JDBC线程资源，不能依赖HTTP收尾。
- Latke IoC 会通过 Javassist 为每个 `@Singleton` Bean 生成子类代理，因此 `@Singleton` 类不能声明为 `final`；新增或修改 Bean 后需检查这一点，否则生产启动可能只显示 `Latke init failed`。
- 项目内 Latke 的 `Cookie#setSameSite` 接收字符串（如 `"Strict"`），不是 Netty 枚举；v3 刷新 Cookie 统一经 `ApiV3RefreshCookies` 构造，真实框架类型检查在 token 专项脚本中。

## 原始请求与响应 Cookie

- 捆绑Latke会有损重写getBytes，存档须经同包`ApiV3GameSaveRawBody`读原Netty字节校验，不能换回框架归一化正文；企业认证JPEG/PNG上传也复用该原始字节适配器，测试必须覆盖parseForm真实有损预处理，不能只setBytes注入。 共享ApiV3WriteInputs也必须从原始Netty字节严格解析，不得回退到getBytes；三认证入口用有界重载（4096字节），刷新/退出先同源再验空体/空对象，格式拒绝不得消费或清Cookie；捆绑Latke会把入站Cookie预填到响应且丢属性，全局Before须先用ApiV3RefreshCookies移除fp_v3_refresh响应副本，只有认证端点显式签发/清除；隔离HTTP夹具须写真实Netty buffer并覆盖parseForm。允许空体的阅读登记同样须校验原始Netty字节：Latke遇非法JSON可能清空getBytes，不能据此当成真正空请求；回归须实际parseForm。

## 路由与请求阶段

- Latke 路由存在静态段被相邻动态段截获的风险（如 `/article/{id}/revisions/list` 可能进入 `/article/{id}/revisions/{revisionId}`）；新增相邻路由时需避免路径歧义，或在处理方法中显式识别保留字。
- `BeforeRequestHandler` 通过 `Dispatcher.startRequestHandler` 在路由中间件前执行；该阶段 `query/form/cookie` 已由 latke-core 解析，可直接读取请求参数与 Cookie。
- `BeforeRequestHandler` 获取当前用户优先用 `UserQueryService#getCurrentUser(request)`（底层 `Sessions.currentUser(request)`）；`Sessions.getUser()` 仅读取 ThreadLocal，未 `Sessions.setUser(...)` 前通常为 `null`。

## 模板数据兼容

- Freemarker + `org.json` 兼容约束：顶层放进 dataModel 的 `List/JSONArray` 可直接 `#list`，但挂在 `JSONObject` 字段里的嵌套 `JSONArray/Iterable` 在 FTL 中可能被包装成非序列；这类数据优先展开成固定字段，或改为顶层列表再渲染。
