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
package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.renderer.AbstractFreeMarkerRenderer;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.model.User;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.Paginator;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.processor.SkinRenderer;
import org.b3log.symphony.processor.middleware.CSRFMidware;
import org.b3log.symphony.processor.middleware.LoginCheckMidware;
import org.b3log.symphony.service.DataModelService;
import org.b3log.symphony.service.MilestoneMgmtService;
import org.b3log.symphony.service.MilestoneQueryService;
import org.b3log.symphony.util.Sessions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.b3log.symphony.cache.MilestoneCache;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Milestone processor.
 * <ul>
 * <li>Shows milestones (/milestones), GET</li>
 * <li>Shows milestone submission page (/milestone/submit), GET</li>
 * <li>Submits a milestone (/milestone/submit), POST</li>
 * <li>Shows admin milestones list (/admin/milestones), GET</li>
 * <li>Shows admin milestone detail (/admin/milestone/{milestoneId}), GET</li>
 * <li>Updates admin milestone (/admin/milestone/{milestoneId}), POST</li>
 * <li>Deletes admin milestone (/admin/milestone/remove), POST</li>
 * <li>Approves milestone (/admin/milestone/approve), POST</li>
 * <li>Rejects milestone (/admin/milestone/reject), POST</li>
 * </ul>
 *
 * @author rhythm
 * @version 1.0.0.0, Jan 15, 2026
 * @since 3.7.0
 */
@Singleton
public class MilestoneProcessor {

    /**
     * Logger.
     */
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(MilestoneProcessor.class);

    @Inject
    private MilestoneQueryService milestoneQueryService;

    @Inject
    private MilestoneMgmtService milestoneMgmtService;

    @Inject
    private DataModelService dataModelService;

    @Inject
    private MilestoneCache milestoneCache;

    /**
     * Register request handlers.
     */
    public static void register() {
        final BeanManager beanManager = BeanManager.getInstance();
        final MilestoneProcessor milestoneProcessor = beanManager.getReference(MilestoneProcessor.class);
        final LoginCheckMidware loginCheck = beanManager.getReference(LoginCheckMidware.class);
        final CSRFMidware csrfMidware = beanManager.getReference(CSRFMidware.class);

        // Public milestone list
        Dispatcher.get("/milestones", milestoneProcessor::showMilestones,loginCheck::handle, csrfMidware::fill);
        Dispatcher.get("/milestones/submit", milestoneProcessor::showSubmitMilestone,loginCheck::handle, csrfMidware::fill);
        Dispatcher.post("/milestones/submit", milestoneProcessor::submitMilestone,loginCheck::handle, csrfMidware::fill);

    }

    /**
     * Shows milestones page.
     */
    public void showMilestones(final RequestContext context) {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "milestones.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        try {
            // Try to get from cache first
            List<JSONObject> timelineEvents = milestoneCache.getTimelineEvents();

            // If cache is empty, load from database and update cache
            if (timelineEvents == null || timelineEvents.isEmpty()) {
                LOGGER.info("Timeline events cache is empty, loading from database");
                milestoneCache.loadTimelineEvents();
                timelineEvents = milestoneCache.getTimelineEvents();
            }

            dataModel.put("timelineEvents", timelineEvents != null ? timelineEvents : new JSONArray());

        } catch (final Exception e) {
            LOGGER.error("Failed to load milestones", e);
            dataModel.put("timelineEvents", new JSONArray());
        }

        dataModelService.fillHeaderAndFooter(context, dataModel);
    }

    public void showSubmitMilestone(final RequestContext context ){
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer(context, "milestone-submit.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();
        dataModelService.fillHeaderAndFooter(context, dataModel);
    }


    /**
     * Submits a milestone.
     */
    public void submitMilestone(final RequestContext context) {
        try {
            final JSONObject currentUser = Sessions.getUser();

            final String title = context.param("title");
            final String date = context.param("date");
            final String endDate = StringUtils.trimToEmpty(context.param("endDate"));
            final String content = context.param("content");
            final String mediaCaption = context.param("mediaCaption");
            final String mediaUrl = StringUtils.trimToEmpty(context.param("mediaUrl"));
            final String mediaType = StringUtils.trimToEmpty(context.param("mediaType"));
            final String link = StringUtils.trimToEmpty(context.param("link"));

            if (StringUtils.isBlank(title) || StringUtils.isBlank(date) || StringUtils.isBlank(content)) {
                context.renderJSON(new JSONObject().put(Keys.CODE, 1).put(Keys.MSG, "标题、日期和内容不能为空"));
                return;
            }

            SimpleDateFormat sdf =  new SimpleDateFormat("yyyy-MM-dd");

            final JSONObject milestone = new JSONObject();
            milestone.put(Milestone.MILESTONE_TITLE, title);
            milestone.put(Milestone.MILESTONE_DATE, sdf.parse(date).getTime());
            if (StringUtils.isNotBlank(endDate)) {
                milestone.put(Milestone.MILESTONE_END_DATE, sdf.parse(endDate).getTime());
            }
            milestone.put(Milestone.MILESTONE_CONTENT, content);
            milestone.put(Milestone.MILESTONE_MEDIA_URL, mediaUrl);
            milestone.put(Milestone.MILESTONE_MEDIA_TYPE, mediaType);
            milestone.put(Milestone.MILESTONE_MEDIA_CAPTION,mediaCaption);
            milestone.put(Milestone.MILESTONE_LINK, link);
            milestone.put(Milestone.MILESTONE_AUTHOR_ID, currentUser.optString(Keys.OBJECT_ID));

            milestoneMgmtService.addMilestone(milestone);

            context.renderJSON(new JSONObject().put(Keys.CODE, 0).put(Keys.MSG, "提交成功，等待审核"));
        } catch (final Exception e) {
            LOGGER.error("Failed to submit milestone", e);
            context.renderJSON(new JSONObject().put(Keys.CODE, 1).put(Keys.MSG, "提交失败：" + e.getMessage()));
        }
    }


}
