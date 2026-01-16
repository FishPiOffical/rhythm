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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Paginator;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.repository.MilestoneRepository;
import org.json.JSONObject;
import org.b3log.latke.repository.*;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

/**
 * Milestone query service.
 *
 * @author rhythm
 * @version 1.0.0.0, Jan 15, 2026
 * @since 3.7.0
 */
@Service
public class MilestoneQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(MilestoneQueryService.class);

    @Inject
    private MilestoneRepository milestoneRepository;

    public List<JSONObject> getAllMilestones(){
        final Query query = new Query().setFilter(new PropertyFilter(Milestone.MILESTONE_STATUS, FilterOperator.EQUAL, Milestone.STATUS_C_APPROVED)
                );
        try {
            return milestoneRepository.getList(query);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Gets milestones failed", e);
            return Collections.emptyList();
        }

    }

    // 根据状态获取里程碑 支持分页 默认50 状态为可选参数
    public JSONObject getMilestonesByStatus(final JSONObject requestJSONObject){

        final JSONObject ret = new JSONObject();

        final int currentPageNum = requestJSONObject.optInt(Pagination.PAGINATION_CURRENT_PAGE_NUM);
        final int pageSize = requestJSONObject.optInt(Pagination.PAGINATION_PAGE_SIZE);
        final int windowSize = requestJSONObject.optInt(Pagination.PAGINATION_WINDOW_SIZE);
        final int status = requestJSONObject.optInt("status");

        final Query query = new Query().setPage(currentPageNum, pageSize).addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);
        if(status !=0){
            query.setFilter(new PropertyFilter(Milestone.MILESTONE_STATUS, FilterOperator.EQUAL, status)
                    );
        }
        JSONObject result;
        try {
            result = milestoneRepository.get(query);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Gets milestone failed", e);
            return null;
        }


        final int pageCount = result.optJSONObject(Pagination.PAGINATION).optInt(Pagination.PAGINATION_PAGE_COUNT);
        final JSONObject pagination = new JSONObject();
        ret.put(Pagination.PAGINATION, pagination);
        final List<Integer> pageNums = Paginator.paginate(currentPageNum, pageSize, pageCount, windowSize);
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        final List<JSONObject> milestones = (List<JSONObject>) result.opt(Keys.RESULTS);
        for (final JSONObject milestone : milestones){
            milestone.put(Milestone.MILESTONE_DATE, sdf.format(milestone.optLong(Milestone.MILESTONE_DATE)));

        }
        ret.put(Milestone.MILESTONES, (Object) milestones);
        return ret;
    }

    public JSONObject getMilestoneById( final String id){
        try {
            return milestoneRepository.get(id);
        } catch (final Exception e) {
            LOGGER.log(Level.ERROR, "Gets milestone failed", e);
            return null;
        }
    }
}
