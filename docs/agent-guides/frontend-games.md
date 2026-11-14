# 前端、皮肤、上传与游戏

本文件为相关任务实施前必读的项目约定，与根目录 `AGENTS.md` 共同生效；迁移不代表重新验证实现或完成人工验收。文中从项目根出发的代码路径保留原写法。

关联阅读：[框架与原始请求](environment-framework.md)、[文章与评论](articles.md)、[安全检查](security-access.md)。

## 源码构建与页面约束

- 前端改动只改源码：`.scss` 与非 `min.js`；执行 `yarn run build` 生成产物；PC/移动端经典皮肤当前目录分别为 `skins/classic/pc` 与 `skins/classic/mobile`，改动需同步评估两端。
- 页面分区的卡片应保持同级关系，禁止使用“卡片套卡片”的嵌套容器；外层布局容器不应再使用卡片背景、边框或阴影包裹内部卡片。
- 鱼排扩展独立脚本需包含标准 `UserScript` 头（至少 `@name`、`@match`、`@grant`、`@run-at`）；聊天室联调脚本建议同时匹配 `https://fishpi.cn/cr*` 与 `http://localhost:8080/cr*`。

## 资源与 CDN 防漏扫

- JS 版本号暴露排查使用 `tools/js-version-audit.mjs`；清理后执行 `yarn run build` 并复查，生产静态资源不要发布可泄露依赖信息的 source map。
- CDN 防漏扫排查：当用户要求“CDN 防漏扫/刷新列表/替换目录”时，扫描项目内 `https://file.fishpi.cn/...` 具体资源（排除 `.codex-tasks`、`target`、`node_modules`、`cdn-version-replacements`），下载可访问资源，检测路径/查询参数/内容中的版本号与 `sourceMappingURL`；命中资源按 CDN 路径镜像到 `cdn-version-replacements/files/`，但路径版本号目录改为稳定目录名（如 `vditor/3.11.1/dist -> vditor/latest/dist`），文件名版本号去掉（如 `jquery.color-2.1.2.min.js -> jquery.color.min.js`），源码引用也同步改成新 URL；版本目录型库必须在 CDN 侧保留原目录并复制完整目录树到稳定目录，不能只上传入口文件；生成 `report.md`、`version-findings.json`、`replacement-validation.json` 和一行一个的 `refresh-urls.txt`；替换副本需复扫并对 JS 执行 `node --check`，下载失败或动态拼接 URL 单独记录，不伪造结果。
- 文章代码高亮由 `src/main/resources/js/common.js` 的 `Util.parseHljs` 统一加载；`vditor/latest` 的 `.min.css` 高亮样式对应 `highlight.min.js` + `third-languages.js`，旧版样式才对应 `highlight.pack.js`。

## 皮肤与模板

- Latke 原生仅提供轻量皮肤元信息能力（如 `Latkes#getSkinName` 读取 `/skins/<dir>/skin.properties`），不负责 Rhythm 的运行时选肤；Rhythm 当前皮肤链路在 `BeforeRequestHandler#resolveSkinDir` + `Sessions` + `processor/SkinRenderer` + `util/Templates`。
- Rhythm 当前“皮肤”本质是模板目录名：默认值来自 `symphony.properties` 的 `skinDirName=classic/pc`、`mobileSkinDirName=classic/mobile`；运行时先按 UA 分流 pc/mobile 默认皮肤，再按用户字段 `userSkin` / `userMobileSkin` 覆盖，设置页通过 `/settings/skin` 保存，`SkinQueryService` 负责扫描 `/skins/**/skin.properties` 识别可选主题。
- Rhythm 侧 `SkinQueryService` 现已按 UTF-8 读取 `skin.properties`，皮肤名称/描述可直接写中文；但 `src/main/resources/lib/latke-core.jar` 内的 `Latkes#getSkinName` 仍是旧 `Properties.load`，若后续有代码依赖该方法读取皮肤元信息，需要同步改 Latke 并替换 jar。
- 模板加载已支持“同设备 fallback”：若自定义皮肤缺少某个 FTL，`Templates` 会回退到默认皮肤的同路径模板（如 `foo/pc/header.ftl -> classic/pc/header.ftl`，`foo/mobile/common/comment.ftl -> classic/mobile/common/comment.ftl`）；因此新增皮肤可以按需覆盖，但至少要提供对应目录与 `skin.properties`。
- 皮肤设置页预览图来自各皮肤目录下 `skin.properties` 的 `previewUrl`；新增皮肤若希望在设置页展示预览图，需要同步配置该字段。
- 前端全局主题变量入口：`src/main/resources/scss/_variables.scss`；`$theme-primary` 会影响 `module`/首页卡片/聊天室等主区背景，深色会导致整站大面积染色；顶栏建议在 `base.scss`/`mobile-base.scss` 的 `.nav` 单独设色。

## 首页布局与数据

- 首页摸鱼倒计时的节假日数据在 `util/Vocation`：数据源为 holiday-cn（jsDelivr/GitHub raw 年度 JSON，国务院官方公告），本地计算 type/dayName/vName/vRest/wRest；不再依赖 timor.tech；刷新失败保留上次成功数据并按周末规则兜底，字段契约与 `/api/vocation` 不变。
- 首页右栏专栏列表（classic/pc）需使用 `module-list long-column-module-list`（见 `skins/classic/pc/index.ftl` 的“最新专栏/热门专栏/最近阅读”）；否则会命中 `.module-list .title` 默认 `margin-left: 30px` 产生左侧空白。
- 首页最新文章链路：`IndexProcessor#loadIndexData` 通过 `ArticleQueryService#getIndexRecentArticles(fetchSize, page)` 组装 classic/pc 与 classic/mobile 首页“最新文章”；第一页置顶插入与数量行为在该方法维护。
- 首页模块个性化链路：classic PC/移动首页模板引入 `home-personalize.js`、`home-modules.js`、`repeater-station.js`，配置状态保存在浏览器 `localStorage`；重置布局要按初始 DOM 锚点恢复，不能简单 append 到父容器末尾；移动端首页参与配置的模块需作为同一个 `home-personalize-zone` 的直接子项；复读机接口在 `RepeaterProcessor`，数据表为 `repeater_content`/`repeater_like`。
- 首页两列对齐约束：`getIndexRecentArticles` 的第一页会插入全部置顶且不截断；第二页起需按第一页“置顶占位数”补偿 `fetchSize` 与分页偏移，并基于已排除置顶 ID 的普通文章序列取数，保证两列等高、不重复、不丢中间文章。
- 首页右侧排行补偿（无前端延迟）：由 `IndexProcessor#loadIndexData` 按两列最新文章的最大行数计算 `rankCompensateRows`，先换算“右栏总补偿行数”再分摊到 `checkinVisibleCount/onlineVisibleCount`，Freemarker 直接按该数量渲染，不再依赖 JS 运行时增删行。

## 上传与游戏

- Evolve 游戏入口是 `/games/evolve/`，PC/移动模板都在 `skins/classic/*/games/evolve/index.ftl`；生产资源使用 `https://file.fishpi.cn/evolve/`，模板需设置 `window.fishpiEvolveAssetBase` 供语言包、Worker 与 Wiki 链接解析；Worker 跨源加载需走同源 Blob 包装后 `importScripts` CDN 脚本；鱼排集成包含鱼游入口、左下角云存档面板、`gameId=40` 排行榜同步与 `gameId=evolve-save` 云存档；不再覆盖 CDN 根 `/main.js`；Evolve CDN 依赖是长缓存，主脚本和与主脚本版本强绑定的库（如 Popper）都需要带 `${staticResourceVersion}`。
- 上传链路：编辑器贴图走 RhyPic 前端直传（`Util.rhypicTicket` + `pic.fishpi.cn/api/v1/files`，字段名 `file`）；头像/裁剪/涂鸦等 `/upload` 代理通道字段名必须是 `file[]`，由 `FileUploadProcessor#uploadViaRhypic` 签票转发。图床拒绝无扩展名文件：FormData 裸 Blob（cropper、聊天室涂鸦）必须带文件名（append 第三参数），后端同时按 Content-Type 为无后缀文件名补扩展名，并在 succMap 保留原名别名兼容 `succMap.blob` 类老前端。前端魔数校验 `isImage` 只认 JPEG(SOI FF D8 FF)/PNG/GIF，WebP 不在 `upload.suffix` 白名单，头像场景给明确提示。
- v3游戏存档仅`ApiV3GameSaveInputs`精确目录，首批evolve-save单槽；`ApiV3GameSaveService`新旧HTTP共锁owner，CAS/metadata回执/版本同严格根，迁移只复制唯一旧记录不覆盖。旧登记游戏保存须JSON、盲写递增版本，其他cloud键保留；先迁移20261025_api_v3_game_save.sql。
- v3 上传签票走 `ApiV3UploadProcessor` / `ApiV3UploadTicketService`，要求 uploads.write，当前用户只取Bearer并从数据库投影重读；`RhypicUploadTickets`只共用Ed25519协议，旧入口字段保持。v3的uploadURL是含/api/v1/files的完整公开地址，不能用internalUrl；短期凭据不落业务幂等表，每次取票新签发。文件直传字段file，旧/upload代理仍是file[]。
