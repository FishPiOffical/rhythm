# 历史、迁移与未验证状态

本文件是历史记录索引，不覆盖当前用户要求和专题中的现行约定。相关任务必须核对实际部署与最新验收证据；记录转移不代表问题已经解决或迁移已执行。

## 已确认的替代关系

- **2026-11-13，开发地址确认：** 当前使用 `http://localhost:8080`。原根文件中 `Rhythm Local` 的Java25、HTTP8081、MySQL `rhythm-dev-mysql`/`127.0.0.1:3319`、8080/3306归属workphone、IPv4/IPv6分占以及用户站临时target `[::1]:8081` 都是旧恢复环境事实，不能套到当前机器。完整过程仍在 [本机恢复记录](../local-idea-environment.md)；历史的“不提交、不推送”只约束当时任务，不覆盖当前本地提交约定。
- **2026-11-06，昵称样式协议替代：** 旧版仅查询指定1..100个ID/profile.read、投影bold/underline/color、无样式空对象的说明已被 [全量会员昵称快照](../api-v3-membership-snapshot-work.md) 替代。当前仍须profile.read，只返回三展示字段的规范化 `configJson`，异常null，内容版本SHA-256、未变不传list；不恢复userIds，不裸返全部权益。历史方案保留在 [旧样式记录](../api-v3-membership-style-work.md)，不能作为当前协议执行。
- **2026-11-13，规则范围澄清：** 旧apiKey文档文案不套到V3 Bearer或独立OpenID/OAuth协议；执行 `test-compile` 仍受Maven授权限制；验证码策略归属 `AnonymousCaptchaShield`，中间件保留身份解析、匿名阅读及受保护委托。没有修改业务行为，也没有重新批准修改验证码盾。

## 迁移与部署前置

各专题中的“先迁移”“须建表”“种子与repository.json同步”等是部署前置条件，不是“本机已执行”的证明；不要因SQL日期较旧删除它们，也不要对有业务数据的库套用恢复环境的删表重建过程。

| 领域 | 前置与相关记录入口 |
| --- | --- |
| 长篇阅读 | [文章专题](articles.md) 的20261030迁移、停登记要求和锁/结算约束 |
| 凭据、MFA、联系方式、OAuth与应用 | [账号专题](auth-account.md)、[凭据工作记录](../api-v3-account-credential-work.md) |
| 红包、聊天事件、外观、Socket版本 | [聊天室专题](chat-online.md) 与其关联工作记录 |
| 会员、职业、积分来源 | [职业会员专题](professions-membership.md)、[职业部署](../profession-production-deployment.md)、[积分专题](points-notifications.md) |
| 游戏存档与原始字节 | [前端游戏专题](frontend-games.md)、[框架专题](environment-framework.md) |
| 本人导出 | [积分专题](points-notifications.md) 的20261024导出表和私有块/回执要求 |

## 未验证、已知风险与范围限制

- [文章专题](articles.md)：长篇段落清洗/媒体仍有夹具边界，不能宣称完整HTTP渲染已验。
- [评论专题](comments-realtime.md)：本人删除与真实MySQL/后台强删竞争仍保留待验证；旧文章编辑同步拦截段落冲突仍有待确认记录，相关任务须查最新用户确认，不可凭本次整理执行。
- [安全专题](security-access.md)：注册/重置页面完整手机号、TopProcessor活动时间戳等已知存量风险仍保留；“用户知情暂不修”不等于修复或重新授权改业务。
- [各模块专题](README.md)：旧写入口未整体共锁、缓存跨节点非强一致、单实例在线不等于全站实时、WebSocket即时事件不可靠补播等限制完整保留，不能扩大结论。
- [验证码验收](captcha-acceptance.md)：Windows/隔离HTTP夹具不能证明真实极验或Linux ipset丢包成功；截图/导出状态与业务结果分列，失败与待确认不得删除。
- V3完成度、源码检查、隔离测试及实际运行验收分开记录，优先查 [进度](../api-v3-progress.md) 与 [当前完成审计](../api-v3-current-completion-audit.md)。本次只整理文档，没有把这些历史状态重标为通过。
