## 项目约定
- 所有文件不要直接修改 `.css`，而是修改 `.scss`，然后执行 `yarn run build` 生成新的 CSS。js同理，请修改js，而不是min.js，然后同样是通过 `yarn run build` 编译min.js
- 如果你的更改涉及到操作数据库，请注意在引用Repository类的时候不要加上包名org.b3log.latke.repository，如果没有请直接import，另外除了查询以外，增、删、改 所有写入的操作都需要用Transaction进行完整的事务commit，否则会无法正常写入。
- 涉及到前端的更改，请不要只修改classic（PC端）皮肤，mobile（移动端）皮肤也要同步修改

## 项目基本结构
- 后端 Java 代码：`src/main/java/org/b3log/symphony`
  - 入口：`Server.java`
  - `processor`：路由/控制层（Web/API）
  - `service`：业务逻辑
  - `repository`：数据访问
  - `model`：领域模型
  - `util`：通用工具
- 资源与配置：`src/main/resources`
  - 配置：`symphony.properties`、`latke.properties`、`log4j2.xml`、`lang_*.properties`
  - 静态资源：`images/`、`js/`、`css/`、`scss/`
  - 模板皮肤：`skins/`（classic / mobile，Freemarker `.ftl`）
- 前端静态输出：`src/main/webapp/css`、`src/main/webapp/js`
- 构建工具：后端 Maven（`pom.xml`）；前端资源 Gulp（`gulpfile.js`、`package.json`）

## 项目基本功能
- 用户与权限：登录/注册、角色权限、MFA、个人设置（User/Role/MFA/Settings）
- 内容与互动：帖子/长文、评论、投票、关注、转发（Article/Comment/Vote/Follow/Forward）
- 话题与聚合：标签、领域（Domain）、城市聚合（Tag/Domain/City）
- 动态与通知：轻量动态（Breezemoon）、站内通知、活动、里程碑、举报（Breezemoon/Notification/Activity/Milestone/Report）
- 运营与积分：勋章、会员、积分与奖励、邀请码、优惠券（Medal/Membership/Point/Reward/Invitecode/Coupon）
- 其他能力：聊天/聊天室、文件上传、搜索、统计、商城、微信支付（Chat/Chatroom/Upload/Search/Statistic/Shop/WeChatPay）
- AI 能力：`AIProviderFactory`（OpenAI 兼容 / 通义千问）
