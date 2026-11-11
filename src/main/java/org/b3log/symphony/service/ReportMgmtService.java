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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.repository.annotation.Transactional;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Notification;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Report;
import org.b3log.symphony.model.RepeaterContent;
import org.b3log.symphony.repository.ReportRepository;
import org.b3log.symphony.repository.RepeaterContentRepository;
import org.b3log.symphony.util.ApiV3ReportInputs;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;

/**
 * Report management service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.2.0.0, Jul 15, 2018
 * @since 3.1.0
 */
@Service
public class ReportMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(ReportMgmtService.class);

    /**
     * Report repository.
     */
    @Inject
    private ReportRepository reportRepository;

    @Inject
    private RepeaterContentRepository repeaterContentRepository;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Pointtransfer management service.
     */
    @Inject
    private PointtransferMgmtService pointtransferMgmtService;

    /**
     * Notification management service.
     */
    @Inject
    private NotificationMgmtService notificationMgmtService;

    /**
     * Makes the specified report as ignored.
     *
     * @param reportId the specified report id
     */
    @Transactional
    public void makeReportIgnored(final String reportId) {
        try {
            final JSONObject report = reportRepository.get(reportId);
            report.put(Report.REPORT_HANDLED, Report.REPORT_HANDLED_C_IGNORED);

            reportRepository.update(reportId, report);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Makes report [id=" + reportId + "] as ignored failed", e);
        }
    }

    /**
     * Makes the specified report as handled.
     *
     * @param reportId the specified report id
     */
    public void makeReportHandled(final String reportId) {
        final Transaction transaction = reportRepository.beginTransaction();
        try {
            final JSONObject report = reportRepository.get(reportId);
            report.put(Report.REPORT_HANDLED, Report.REPORT_HANDLED_C_YES);

            reportRepository.update(reportId, report);
            transaction.commit();

            final String reporterId = report.optString(Report.REPORT_USER_ID);
            final String transferId = pointtransferMgmtService.transfer(Pointtransfer.ID_C_SYS, reporterId,
                    Pointtransfer.TRANSFER_TYPE_C_REPORT_HANDLED, Pointtransfer.TRANSFER_SUM_C_REPORT_HANDLED, reportId, System.currentTimeMillis(), "");

            final JSONObject notification = new JSONObject();
            notification.put(Notification.NOTIFICATION_USER_ID, reporterId);
            notification.put(Notification.NOTIFICATION_DATA_ID, transferId);
            notificationMgmtService.addReportHandledNotification(notification);
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.ERROR, "Makes report [id=" + reportId + "] as handled failed", e);
        }
    }

    /**
     * Adds a report.
     *
     * @param report the specified report, for example,
     *               {
     *               "reportUserId": "",
     *               "reportDataId": "",
     *               "reportDataType": int,
     *               "reportType": int,
     *               "reportMemo": ""
     *               }
     * @throws ServiceException service exception
     */
    @Transactional
    public void addReport(final JSONObject report) throws ServiceException {
        report.put(Report.REPORT_HANDLED, Report.REPORT_HANDLED_C_NOT);

        try {
            if (Report.REPORT_DATA_TYPE_C_REPEATER == report.optInt(Report.REPORT_DATA_TYPE)) {
                final JSONObject normalized;
                try {
                    normalized = ApiV3ReportInputs.normalize(new JSONObject().put("targetType", 4)
                            .put("targetId", report.opt(Report.REPORT_DATA_ID)).put("reason", report.opt(Report.REPORT_TYPE))
                            .put("memo", report.opt(Report.REPORT_MEMO)));
                } catch (final ApiV3Exception e) {
                    throw new ServiceException("举报参数不合法");
                }
                final String targetId = normalized.getString("targetId");
                final var rows = repeaterContentRepository.select("SELECT repeaterContentStatus,repeaterContentAuthorId FROM `"
                        + repeaterContentRepository.getName() + "` WHERE oId=? FOR UPDATE", targetId);
                if (rows.size() != 1 || rows.getFirst().optInt(RepeaterContent.STATUS) != RepeaterContent.STATUS_C_VALID) {
                    throw new ServiceException("内容不存在或已删除");
                }
                if (rows.getFirst().optString(RepeaterContent.AUTHOR_ID).equals(report.optString(Report.REPORT_USER_ID))) {
                    throw new ServiceException("不能举报自己的内容");
                }
                if (!reportRepository.select("SELECT oId FROM `" + reportRepository.getName()
                        + "` WHERE reportUserId=? AND reportDataType=4 AND reportDataId=? AND reportHandled=0 LIMIT 1 FOR UPDATE",
                        report.getString(Report.REPORT_USER_ID), targetId).isEmpty()) return;
                report.put(Report.REPORT_MEMO, org.apache.commons.lang.StringEscapeUtils.escapeHtml(normalized.getString("memo")));
            }
            reportRepository.add(report);
        } catch (final ServiceException e) {
            throw e;
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Adds a report failed", e);

            throw new ServiceException(langPropsService.get("systemErrLabel"));
        }
    }
}
