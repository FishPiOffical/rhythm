/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.service;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Paginator;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Report;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.Emotions;
import org.b3log.symphony.util.Escapes;
import org.b3log.symphony.util.Markdowns;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Report management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.2, Jun 27, 2018
 * @since 3.1.0
 */
@Service
public class ReportQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ReportQueryService.class);

    /**
     * Report repository.
     */
    @Inject
    private ReportRepository reportRepository;

    /**
     * User repository.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Article repository.
     */
    @Inject
    private ArticleRepository articleRepository;

    /**
     * Comment repository.
     */
    @Inject
    private CommentRepository commentRepository;

    @Inject
    private RepeaterContentRepository repeaterContentRepository;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Comment query service.
     */
    @Inject
    private CommentQueryService commentQueryService;

    /**
     * Chat Room Service.
     */
    @Inject
    private ChatRoomService chatRoomService;

    @Inject
    private LogsRepository logsRepository;

    JSONObject findChatRevokeLog(final String dataId) throws RepositoryException {
        final List<JSONObject> preciseRows = logsRepository.select(
                "SELECT * FROM " + logsRepository.getName()
                        + " WHERE type=? AND key3=? ORDER BY oId DESC LIMIT 1", "cr-revoke", dataId);
        if (!preciseRows.isEmpty()) {
            return preciseRows.get(0);
        }
        // 历史日志按 JSON 中的消息 ID 匹配，转义 LIKE 通配符，避免扩大查询范围。
        final String escapedId = dataId.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        final List<JSONObject> fallback = logsRepository.select(
                "SELECT * FROM " + logsRepository.getName()
                        + " WHERE type=? AND data LIKE ? ESCAPE '!' ORDER BY oId DESC LIMIT 1",
                "cr-revoke", "%\"oId\":\"" + escapedId + "\"%");
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    private static String renderChatReport(final JSONObject content, final boolean revoked) {
        // 审计原文保持不变，仅在管理展示边界转义身份、清洗正文，两端模板共用安全 HTML。
        final String notice = revoked ? "[该数据已被撤回或删除，此为快照记录] " : "";
        final String author = Escapes.escapeHTML(content.optString(User.USER_NAME));
        // 使用本地私有渲染，保留 Markdown、图片和换行，避免审计正文进入公开缓存或远程转换服务。
        return "<p>" + notice + "[" + author + "]</p>"
                + Markdowns.toPrivateHTML(content.optString(Common.CONTENT));
    }

    /**
     * Gets report by the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     *                          {
     *                          "paginationCurrentPageNum": 1,
     *                          "paginationPageSize": 20,
     *                          "paginationWindowSize": 10
     *                          }, see {@link Pagination} for more details
     * @return for example,      <pre>
     * {
     *     "pagination": {
     *         "paginationPageCount": 100,
     *         "paginationPageNums": [1, 2, 3, 4, 5]
     *     },
     *     "reports": [{
     *         "oId": "",
     *         "reportUserName": "<a>/member/username</a>",
     *         "reportData": "<a>Article or user</a>",
     *         "reportDataType": int,
     *         "reportDataTypeStr": "",
     *         "reportType": int,
     *         "reportTypeStr": "",
     *         "reportMemo": "",
     *         "reportHandled": int,
     *
     *      }, ....]
     * }
     * </pre>
     * @see Pagination
     */
    public JSONObject getReports(final JSONObject requestJSONObject) {
        final JSONObject ret = new JSONObject();

        final int currentPageNum = requestJSONObject.optInt(Pagination.PAGINATION_CURRENT_PAGE_NUM);
        final int pageSize = requestJSONObject.optInt(Pagination.PAGINATION_PAGE_SIZE);
        final int windowSize = requestJSONObject.optInt(Pagination.PAGINATION_WINDOW_SIZE);
        final Query query = new Query().setPage(currentPageNum, pageSize).
                addSort(Report.REPORT_HANDLED, SortDirection.ASCENDING).
                addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        JSONObject result;
        try {
            result = reportRepository.get(query);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Get reports failed", e);
            return null;
        }

        final int pageCount = result.optJSONObject(Pagination.PAGINATION).optInt(Pagination.PAGINATION_PAGE_COUNT);
        final JSONObject pagination = new JSONObject();
        ret.put(Pagination.PAGINATION, pagination);
        final List<Integer> pageNums = Paginator.paginate(currentPageNum, pageSize, pageCount, windowSize);
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        final List<JSONObject> records = (List<JSONObject>) result.opt(Keys.RESULTS);
        final List<JSONObject> reports = new ArrayList<>();
        for (final JSONObject record : records) {
            String chatContent = "";
            final JSONObject report = new JSONObject();
            report.put(Keys.OBJECT_ID, record.optString(Keys.OBJECT_ID));
            try {
                final String reportUserId = record.optString(Report.REPORT_USER_ID);
                final JSONObject reporter = userRepository.get(reportUserId);
                report.put(Report.REPORT_T_USERNAME, UserExt.getUserLink(reporter));
                report.put(Report.REPORT_T_TIME, new Date(record.optLong(Keys.OBJECT_ID)));

                final String dataId = record.optString(Report.REPORT_DATA_ID);
                final int dataType = record.optInt(Report.REPORT_DATA_TYPE);
                report.put(Report.REPORT_DATA_TYPE, dataType);
                String reportData = langPropsService.get("removedLabel");
                switch (dataType) {
                    case Report.REPORT_DATA_TYPE_C_REPEATER:
                        report.put(Report.REPORT_T_DATA_TYPE_STR, "复读内容");
                        final JSONObject repeater = repeaterContentRepository.get(dataId);
                        if (null != repeater) {
                            reportData = "<div style=\"white-space:pre-wrap\">"
                                    + Escapes.escapeHTML(repeater.optString(RepeaterContent.CONTENT)) + "</div>";
                            if (RepeaterContent.STATUS_C_VALID == repeater.optInt(RepeaterContent.STATUS)
                                    && dataId.matches("[1-9][0-9]{0,18}")) {
                                report.put("repeaterId", dataId);
                            }
                        }
                        break;
                    case Report.REPORT_DATA_TYPE_C_ARTICLE:
                        report.put(Report.REPORT_T_DATA_TYPE_STR, langPropsService.get("articleLabel"));
                        final JSONObject article = articleRepository.get(dataId);
                        if (null != article) {
                            final String title = Escapes.escapeHTML(article.optString(Article.ARTICLE_TITLE));
                            reportData = "<a href=\"" + Latkes.getServePath() + "/article/" + article.optString(Keys.OBJECT_ID) +
                                    "\" target=\"_blank\">" + Emotions.convert(title) + "</a>";
                        }
                        break;
                    case Report.REPORT_DATA_TYPE_C_COMMENT:
                        report.put(Report.REPORT_T_DATA_TYPE_STR, langPropsService.get("cmtLabel"));
                        final JSONObject comment = commentRepository.get(dataId);
                        if (null != comment) {
                            final String commentId = comment.optString(Keys.OBJECT_ID);
                            final int cmtViewMode = UserExt.USER_COMMENT_VIEW_MODE_C_REALTIME;
                            reportData = commentQueryService.getCommentURL(commentId, cmtViewMode, Symphonys.ARTICLE_COMMENTS_CNT);
                        }
                        break;
                    case Report.REPORT_DATA_TYPE_C_USER:
                        report.put(Report.REPORT_T_DATA_TYPE_STR, langPropsService.get("accountLabel"));
                        final JSONObject reported = userRepository.get(dataId);
                        reportData = UserExt.getUserLink(reported);
                        break;
                    case Report.REPORT_DATA_TYPE_C_CHAT:
                        report.put(Report.REPORT_T_DATA_TYPE_STR, langPropsService.get("chatLabel"));
                        final JSONObject redPacket = chatRoomService.getChatMsg(dataId);
                        if (redPacket == null) {
                            try {
                                final JSONObject log = findChatRevokeLog(dataId);
                                if (log != null) {
                                    final JSONObject content = new JSONObject(new JSONObject(log.optString("data")).optString(Common.CONTENT));
                                    chatContent = renderChatReport(content, true);
                                } else {
                                    chatContent = "内容已被撤回或删除，但日志中无记录，请联系管理员调取。原聊天数据oId为：" + Escapes.escapeHTML(dataId);
                                }
                            } catch (Exception e) {
                                chatContent = "内容已被撤回或删除，但日志中无记录，请联系管理员调取。原聊天数据oId为：" + Escapes.escapeHTML(dataId);
                            }
                        } else {
                            final JSONObject content = new JSONObject(redPacket.optString(Common.CONTENT));
                            chatContent = renderChatReport(content, false);
                        }
                        reportData = "<a href=\"" + Latkes.getServePath() + "/cr?oId=" + URLEncoder.encode(dataId, StandardCharsets.UTF_8) +
                                "\" target=\"_blank\">跳转至该消息</a>";
                        break;
                    default:
                        LOGGER.log(Level.ERROR, "Unknown report data type [" + dataType + "]");
                        continue;
                }
                report.put(Report.REPORT_T_DATA, reportData);
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Builds report data failed", e);
                continue;
            }

            final int type = record.optInt(Report.REPORT_TYPE);
            report.put(Report.REPORT_TYPE, type);
            switch (type) {
                case Report.REPORT_TYPE_C_SPAM_AD:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("spamADLabel"));
                    break;
                case Report.REPORT_TYPE_C_PORNOGRAPHIC:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("pornographicLabel"));
                    break;
                case Report.REPORT_TYPE_C_VIOLATION_OF_REGULATIONS:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("violationOfRegulationsLabel"));
                    break;
                case Report.REPORT_TYPE_C_ALLEGEDLY_INFRINGING:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("allegedlyInfringingLabel"));
                    break;
                case Report.REPORT_TYPE_C_PERSONAL_ATTACKS:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("personalAttacksLabel"));
                    break;
                case Report.REPORT_TYPE_C_POSING_ACCOUNT:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("posingAccountLabel"));
                    break;
                case Report.REPORT_TYPE_C_SPAM_AD_ACCOUNT:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("spamADAccountLabel"));
                    break;
                case Report.REPORT_TYPE_C_PERSONAL_INFO_VIOLATION:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("personalInfoViolationLabel"));
                    break;
                case Report.REPORT_TYPE_C_OTHER:
                    report.put(Report.REPORT_T_TYPE_STR, langPropsService.get("miscLabel"));
                    break;
                default:
                    LOGGER.log(Level.ERROR, "Unknown report type [" + type + "]");
                    continue;
            }

            if (!chatContent.isEmpty()) {
                report.put(Report.REPORT_MEMO, chatContent);
            } else {
                String memo = record.optString(Report.REPORT_MEMO);
                memo = Markdowns.toHTML(memo);
                memo = Markdowns.clean(memo, "");
                report.put(Report.REPORT_MEMO, memo);
            }
            report.put(Report.REPORT_HANDLED, record.optInt(Report.REPORT_HANDLED));
            reports.add(report);
        }
        ret.put(Report.REPORTS, (Object) reports);
        return ret;
    }
}
