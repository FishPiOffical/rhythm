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
package org.b3log.symphony.cache;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.service.DomainQueryService;
import org.b3log.symphony.service.MilestoneQueryService;
import org.json.JSONObject;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.repository.MilestoneRepository;
import org.b3log.symphony.util.JSONs;
import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Milestone cache.
 *
 * @author rhythm
 * @version 1.0.0.0, Jan 16, 2026
 * @since 3.7.0
 */
@Singleton
public class MilestoneCache {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(MilestoneCache.class);

    /**
     * Timeline events cache key.
     */
    private static final String TIMELINE_EVENTS_CACHE_KEY = "timeline_events";

    /**
     * Timeline events cache.
     */
    private List<JSONObject> timelineEventsCache = new ArrayList<>();


    @Inject
    private MilestoneQueryService milestoneQueryService;

    /**
     * Gets timeline events from cache.
     *
     * @return timeline events
     */
    public List<JSONObject> getTimelineEvents() {
        if (timelineEventsCache.isEmpty()) {
            return null;
        }

        return JSONs.clone(timelineEventsCache);
    }

    /**
     * Loads timeline events from database and updates cache.
     */
    public void loadTimelineEvents() {

        try {
            final List<JSONObject> milestones = milestoneQueryService.getAllMilestones();
            final List<JSONObject> timelineEvents = new ArrayList<>();
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            milestones.forEach(milestone -> {
                final JSONObject event = new JSONObject();

                event.put("id", milestone.optString(Milestone.MILESTONE_ID));
                event.put("title", milestone.optString(Milestone.MILESTONE_TITLE));
                event.put("date", dateFormat.format(new Date(milestone.optLong(Milestone.MILESTONE_DATE))));

                // If has end date, add as time period
                final long endDateLong = milestone.optLong(Milestone.MILESTONE_END_DATE);
                if (endDateLong > 0) {
                    event.put("end_date", dateFormat.format(new Date(endDateLong)));
                }

                event.put("content", milestone.optString(Milestone.MILESTONE_CONTENT));

                final String mediaUrl = milestone.optString(Milestone.MILESTONE_MEDIA_URL);
                if (!mediaUrl.isEmpty()) {
                    event.put("media", new JSONObject()
                            .put("url", mediaUrl)
                            .put("caption", milestone.optString(Milestone.MILESTONE_MEDIA_CAPTION))
                            .put("type", milestone.optString(Milestone.MILESTONE_MEDIA_TYPE, "image")));
                }

                final String link = milestone.optString(Milestone.MILESTONE_LINK);
                if (!link.isEmpty()) {
                    event.put("link", new JSONObject().put("url", link).put("text", "查看详情"));
                }

                timelineEvents.add(event);
            });

            timelineEventsCache.clear();
            timelineEventsCache.addAll(timelineEvents);

            LOGGER.info("Loaded {} timeline events into cache", timelineEvents.size());
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Failed to load timeline events into cache", e);
        }
    }

    /**
     * Clears the timeline events cache.
     */
    public void clearTimelineEventsCache() {
        timelineEventsCache.clear();
        LOGGER.info("Cleared timeline events cache");
    }
}
