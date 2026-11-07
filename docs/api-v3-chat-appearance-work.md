# 聊天外观发送时快照

当前偏好存 user_chat_appearance，oId 为稳定用户ID；消息快照保存在既有 chat_room content JSON，单条写入与消息、通知/事件共事务。共享保存原语不信任调用方 appearance，覆盖为当前有效配置，普通/卡片/旧发布入口均覆盖。历史DTO不读取作者当前外观或会员资格。

先备份数据库并执行 sql/20261107_api_v3_chat_appearance.sql（按 jdbc.tablePrefix 调整），再以 Java 25 在 IDEA 重建重启；不需要消息表迁移或历史回填。缺表会使新消息写入失败。未执行线上迁移、部署或真实站点写测试。

第一方 proof 在幂等回放前校验，settings.write 写明确 ID 对，profile.read 读当前。当前选择与消息保存以本人用户行串行，VIP 在实际保存时校验。旧消息默认 classic/none，已存 VIP 快照不因到期降级。version=1 为固定目录版本，不是用户配置计数；未来版本必须保留旧视觉。

验证：扩展既有 tools/api-v3-profile-tags-test.mjs 执行新 ApiV3ChatAppearanceTest（目录、四消息历史、幂等/权限、过期、回滚、HTTP）及原专项；隔离 javac 编译生产相关类，并执行共享 ChatMessageMgmtServiceTest，验证伪造快照被覆盖和事务回滚。未使用 Maven。前端 109 项专项及完整构建通过；浏览器本地夹具换装/刷新保持历史。

人工：迁移并 IDEA 重建重启后，A 设置橙色发送两条、换蓝色再发两条，B 历史/刷新/实时应看到两组外观；VIP 到期只影响新消息；验证设置断线原键恢复、换号隔离与旧消息默认。真实 MySQL、双账号端到端尚未执行。API.md 需同步公开API文章。
