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
package org.b3log.symphony.repository;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Pointtransfer;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pointtransfer repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.1.0, Dec 12, 2016
 * @since 1.3.0
 */
@Repository
public class PointtransferRedcRepository extends AbstractRepository {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(PointtransferRedcRepository.class);

    /**
     * Single-thread executor for async redc insert.
     */
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    /**
     * Public constructor.
     */
    public PointtransferRedcRepository() {
        super("pointtransfer_redc");
    }

    /**
     * 新增一条冗余表记录
     *
     * @param userId 关联用户ID
     * @param rawId  主表转账ID
     * @return 新增后的 JSONObject
     * @throws RepositoryException repository exception
     */
    public static String addRecord(final String userId, final String rawId) throws RepositoryException{
        final BeanManager beanManager = BeanManager.getInstance();
        final PointtransferRedcRepository pointtransferRedcRepository = beanManager.getReference(PointtransferRedcRepository.class);
        JSONObject obj = new JSONObject();
        obj.put("userId", userId);
        obj.put("rawId", rawId);
        return pointtransferRedcRepository.add(obj);
    }

    /**
     * 新增一条冗余表记录（异步），不阻塞调用线程。
     *
     * 异常会被记录日志，但不会影响主流程。
     *
     * @param userId 关联用户ID
     * @param rawId  主表转账ID
     */
    public static void addRecordAsync(final String userId, final String rawId) {
        ASYNC_EXECUTOR.submit(() -> {
            try {
                final BeanManager beanManager = BeanManager.getInstance();
                final PointtransferRedcRepository pointtransferRedcRepository = beanManager.getReference(PointtransferRedcRepository.class);
                final Transaction transaction = pointtransferRedcRepository.beginTransaction();
                JSONObject obj = new JSONObject();
                obj.put("userId", userId);
                obj.put("rawId", rawId);
                pointtransferRedcRepository.add(obj);
                transaction.commit();
            } catch (final Exception e) {
                LOGGER.log(Level.ERROR, "Async add redc record failed [userId=" + userId + ", rawId=" + rawId + "]", e);
            }
        });
    }

    /**
     * 分页查询某用户的冗余转账记录
     *
     * @param userId 用户ID
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页条数
     * @return List<JSONObject>
     * @throws RepositoryException repository exception
     */
    public JSONObject getByUserId(final String userId, final int pageNum, final int pageSize) throws RepositoryException {
        Query query = new Query()
                .addSort(Keys.OBJECT_ID, SortDirection.DESCENDING)
                .setPage(pageNum, pageSize)
                .setFilter(new PropertyFilter("userId", FilterOperator.EQUAL, userId));
        return get(query);
    }

    public static JSONObject getPointtransferByUserId(final String userId, final int pageNum, final int pageSize) throws RepositoryException {
        final BeanManager beanManager = BeanManager.getInstance();
        final PointtransferRedcRepository pointtransferRedcRepository = beanManager.getReference(PointtransferRedcRepository.class);
        final PointtransferRepository pointtransferRepository = beanManager.getReference(PointtransferRepository.class);

        // 1. 查询冗余表，拿到分页信息和rslts
        JSONObject redcResult = pointtransferRedcRepository.getByUserId(userId, pageNum, pageSize);
        List<JSONObject> redcRslts = (List<JSONObject>) redcResult.opt("rslts");
        if (redcRslts == null || redcRslts.isEmpty()) {
            // 保留分页信息，rslts为空
            JSONObject ret = new JSONObject();
            ret.put("pagination", redcResult.opt("pagination"));
            ret.put("rslts", new ArrayList<>());
            return ret;
        }

        // 2. 提取rawId列表
        List<String> rawIds = new ArrayList<>();
        for (JSONObject obj : redcRslts) {
            rawIds.add(obj.optString("rawId"));
        }

        // 3. 批量查主表
        Query query = new Query()
                .setFilter(new PropertyFilter(Keys.OBJECT_ID, FilterOperator.IN, rawIds));
        List<JSONObject> pointtransfers = pointtransferRepository.getList(query);

        // 4. 用rawId做映射
        Map<String, JSONObject> rawId2Transfer = new HashMap<>();
        for (JSONObject pt : pointtransfers) {
            rawId2Transfer.put(pt.optString(Keys.OBJECT_ID), pt);
        }

        // 5. 按原顺序组装rslts
        List<JSONObject> realRslts = new ArrayList<>();
        for (JSONObject obj : redcRslts) {
            String rawId = obj.optString("rawId");
            JSONObject real = rawId2Transfer.get(rawId);
            if (real != null) {
                realRslts.add(real);
            }
        }

        // 6. 返回带分页信息的新JSONObject
        JSONObject ret = new JSONObject();
        ret.put("pagination", redcResult.opt("pagination"));
        ret.put("rslts", realRslts);
        return ret;
    }


}
