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
import org.b3log.latke.repository.CompositeFilterOperator;
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.util.Dates;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 勋章业务服务.
 */
@Service
public class MedalService {

    private static final Logger LOGGER = LogManager.getLogger(MedalService.class);

    private static class MigrationProgress {
        private volatile int total;
        private volatile int current;
        private volatile String status;
        private volatile String message;

        private MigrationProgress() {
            this.total = 0;
            this.current = 0;
            this.status = "INIT";
            this.message = "";
        }
    }

    private static final MigrationProgress MEDAL_MIGRATION_PROGRESS = new MigrationProgress();

    @Inject
    private MedalRepository medalRepository;

    @Inject
    private UserMedalRepository userMedalRepository;

    @Inject
    private CloudRepository cloudRepository;

    /**
     * 如果 user_medal 记录已过期则删除.
     */
    private void removeExpiredUserMedalIfNeeded(final JSONObject userMedal) {
        if (userMedal == null || userMedal.length() == 0) {
            return;
        }
        long expireTime = userMedal.optLong("expire_time", 0L);
        if (expireTime <= 0L) {
            // 0 表示永久，不过期
            return;
        }
        long now = System.currentTimeMillis();
        if (expireTime > now) {
            return;
        }
        String oId = userMedal.optString("oId");
        if (oId == null || oId.isEmpty()) {
            return;
        }
        try {
            Transaction tx = userMedalRepository.beginTransaction();
            userMedalRepository.remove(oId);
            tx.commit();
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to remove expired user medal [" + oId + "]", e);
        }
    }

    /**
     * 查询指定用户勋章列表
     * 调用方式：传入 userId，返回该用户已拥有的所有勋章信息列表（已自动关联 medal 表信息）
     *
     * @param userId 用户 ID
     * @return 勋章列表
     */
    public List<JSONObject> getUserMedals(final String userId) {
        try {
            long now = System.currentTimeMillis();
            Query userMedalQuery = new Query()
                    .setFilter(new PropertyFilter("user_id", FilterOperator.EQUAL, userId));
            List<JSONObject> userMedals = userMedalRepository.getList(userMedalQuery);
            if (userMedals.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> medalIds = new ArrayList<>();
            List<JSONObject> validUserMedals = new ArrayList<>();
            for (JSONObject userMedal : userMedals) {
                long expireTime = userMedal.optLong("expire_time", 0L);
                if (expireTime > 0L && expireTime <= now) {
                    // 已过期，直接删除，不用返回
                    removeExpiredUserMedalIfNeeded(userMedal);
                    continue;
                }
                String medalId = userMedal.optString("medal_id");
                if (medalId != null && !medalId.isEmpty()) {
                    medalIds.add(medalId);
                    validUserMedals.add(userMedal);
                }
            }
            if (medalIds.isEmpty()) {
                return new ArrayList<>();
            }
            List<JSONObject> result = new ArrayList<>();
            for (String medalId : medalIds) {
                Query medalQuery = new Query()
                        .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
                List<JSONObject> medals = medalRepository.getList(medalQuery);
                for (JSONObject medal : medals) {
                    JSONObject medalCopy = new JSONObject(medal.toString());
                    for (JSONObject userMedal : validUserMedals) {
                        if (medalId.equals(userMedal.optString("medal_id"))) {
                            medalCopy.put("user_medal_oId", userMedal.optString("oId"));
                            medalCopy.put("user_id", userMedal.optString("user_id"));
                            medalCopy.put("expire_time", userMedal.optLong("expire_time"));
                            medalCopy.put("display", userMedal.optBoolean("display", true));
                            medalCopy.put("display_order", userMedal.optInt("display_order", 0));
                            medalCopy.put("data", userMedal.optString("data", ""));
                            // 渲染勋章
                            renderMetal(medalCopy);
                            break;
                        }
                    }
                    result.add(medalCopy);
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to get user medals for user [" + userId + "]", e);
            return new ArrayList<>();
        }
    }

    /**
     * 查询指定用户未过期的勋章列表
     * 调用方式：传入 userId，返回该用户当前仍然有效（未过期）的所有勋章信息列表
     *
     * @param userId 用户 ID
     * @return 未过期勋章列表
     */
    public List<JSONObject> getUserValidMedals(final String userId) {
        long now = System.currentTimeMillis();
        List<JSONObject> all = getUserMedals(userId);
        List<JSONObject> valid = new ArrayList<>();
        for (JSONObject medal : all) {
            long expireTime = medal.optLong("expire_time", 0L);
            if (expireTime == 0L || expireTime > now) {
                valid.add(medal);
            }
        }
        return valid;
    }

    /**
     * 批量更新用户勋章显示顺序
     * 调用方式：传入 userId 和排序配置 JSON，排序配置格式为 {"medalOrders":[{"medalId":"xxx","order":1},...]}
     *
     * @param userId     用户 ID
     * @param sortConfig 排序配置 JSON
     * @throws ServiceException 操作失败时抛出
     */
    public void updateUserMedalOrder(final String userId,
                                     final JSONObject sortConfig) throws ServiceException {
        Transaction transaction = userMedalRepository.beginTransaction();
        try {
            JSONArray medalOrders = sortConfig.optJSONArray("medalOrders");
            if (medalOrders == null) {
                transaction.commit();
                return;
            }
            for (int i = 0; i < medalOrders.length(); i++) {
                JSONObject item = medalOrders.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String medalId = item.optString("medalId");
                int order = item.optInt("order", 0);
                if (medalId == null || medalId.isEmpty()) {
                    continue;
                }
                Query query = new Query()
                        .setFilter(CompositeFilterOperator.and(
                                new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                                new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId)
                        ));
                JSONObject existing = userMedalRepository.getFirst(query);
                if (existing == null || existing.length() == 0) {
                    continue;
                }
                String oId = existing.optString("oId");
                existing.put("display_order", order);
                userMedalRepository.update(oId, existing);
            }
            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.log(Level.ERROR, "Failed to update medal order for user [" + userId + "]", e);
            throw new ServiceException("Failed to update medal order");
        }
    }

    /**
     * 单个更新用户勋章显示顺序（根据方向上移/下移，并重新整理顺序）.
     *
     * @param userId    用户 ID
     * @param medalId   勋章 ID
     * @param direction "up" 或 "down"
     */
    public void reorderUserMedalSingle(final String userId,
                                       final String medalId,
                                       final String direction) throws ServiceException {
        Transaction transaction = userMedalRepository.beginTransaction();
        try {
            // 取出该用户所有勋章，按 display_order 升序
            Query allQuery = new Query()
                    .setFilter(new PropertyFilter("user_id", FilterOperator.EQUAL, userId));
            List<JSONObject> allUserMedals = userMedalRepository.getList(allQuery);
            if (allUserMedals == null || allUserMedals.isEmpty()) {
                transaction.commit();
                return;
            }
            allUserMedals.sort((o1, o2) -> {
                int o1Order = o1.optInt("display_order", 0);
                int o2Order = o2.optInt("display_order", 0);
                return Integer.compare(o1Order, o2Order);
            });

            // 找到目标 medal 在列表中的下标
            int targetIndex = -1;
            for (int i = 0; i < allUserMedals.size(); i++) {
                JSONObject um = allUserMedals.get(i);
                if (medalId.equals(um.optString("medal_id"))) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex == -1) {
                transaction.commit();
                return;
            }

            // 根据方向做一次相邻交换
            if ("up".equals(direction)) {
                if (targetIndex == 0) {
                    // 已经在最前，不能再上移
                } else {
                    JSONObject tmp = allUserMedals.get(targetIndex - 1);
                    allUserMedals.set(targetIndex - 1, allUserMedals.get(targetIndex));
                    allUserMedals.set(targetIndex, tmp);
                }
            } else if ("down".equals(direction)) {
                if (targetIndex >= allUserMedals.size() - 1) {
                    // 已经在最后，不能再下移
                } else {
                    JSONObject tmp = allUserMedals.get(targetIndex + 1);
                    allUserMedals.set(targetIndex + 1, allUserMedals.get(targetIndex));
                    allUserMedals.set(targetIndex, tmp);
                }
            }

            // 从 0 开始重新捋一遍 display_order
            int order = 0;
            for (JSONObject um : allUserMedals) {
                String oId = um.optString("oId");
                if (oId == null || oId.isEmpty()) {
                    continue;
                }
                um.put("display_order", order++);
                userMedalRepository.update(oId, um);
            }

            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.log(Level.ERROR,
                    "Failed to reorder single medal for user [" + userId + "], medalId [" + medalId + "], direction [" + direction + "]", e);
            throw new ServiceException("Failed to reorder single medal");
        }
    }

    /**
     * 查询指定用户有效且已开启展示的勋章列表
     * 调用方式：传入 userId，返回该用户当前仍然有效且 display=true 的勋章信息列表，按 display_order 从小到大排序
     *
     * @param userId 用户 ID
     * @return 未过期且展示中的勋章列表
     */
    public List<JSONObject> getUserDisplayedValidMedals(final String userId) {
        long now = System.currentTimeMillis();
        try {
            Query userMedalQuery = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                            new PropertyFilter("display", FilterOperator.EQUAL, true)
                    ));
            List<JSONObject> userMedals = userMedalRepository.getList(userMedalQuery);
            if (userMedals.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> medalIds = new ArrayList<>();
            List<JSONObject> filteredUserMedals = new ArrayList<>();
            for (JSONObject userMedal : userMedals) {
                long expireTime = userMedal.optLong("expire_time", 0L);
                if (expireTime != 0L && expireTime <= now) {
                    // 已过期的直接删除
                    removeExpiredUserMedalIfNeeded(userMedal);
                    continue;
                }
                String medalId = userMedal.optString("medal_id");
                if (medalId != null && !medalId.isEmpty()) {
                    medalIds.add(medalId);
                    filteredUserMedals.add(userMedal);
                }
            }
            if (medalIds.isEmpty()) {
                return new ArrayList<>();
            }
            List<JSONObject> result = new ArrayList<>();
            for (JSONObject userMedal : filteredUserMedals) {
                String medalId = userMedal.optString("medal_id");
                Query medalQuery = new Query()
                        .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
                List<JSONObject> medals = medalRepository.getList(medalQuery);
                for (JSONObject medal : medals) {
                    JSONObject medalCopy = new JSONObject(medal.toString());
                    medalCopy.put("user_medal_oId", userMedal.optString("oId"));
                    medalCopy.put("user_id", userMedal.optString("user_id"));
                    medalCopy.put("expire_time", userMedal.optLong("expire_time"));
                    medalCopy.put("display", userMedal.optBoolean("display", true));
                    medalCopy.put("display_order", userMedal.optInt("display_order", 0));
                    medalCopy.put("data", userMedal.optString("data", ""));
                    // 渲染勋章
                    renderMetal(medalCopy);
                    result.add(medalCopy);
                }
            }
            result.sort((o1, o2) -> {
                int order1 = o1.optInt("display_order", 0);
                int order2 = o2.optInt("display_order", 0);
                return Integer.compare(order1, order2);
            });
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.ERROR, "Failed to get displayed valid medals for user [" + userId + "]", e);
            return new ArrayList<>();
        }
    }
    
    public void renderMetal(JSONObject metal) {
        String description = metal.optString("medal_description");
        String data = metal.optString("data");
        /**
         * medal_description: 勋章描述 支持{var1} {var2} ... 零个或多个变量
         * data: 勋章数据 每个值使用;分隔，分割后将数据填充到medal_description，然后保存回metal
         */
        if (description != null && !description.isEmpty() && data != null && !data.isEmpty()) {
            String[] vars = data.split(";");
            for (int i = 0; i < vars.length; i++) {
                String var = vars[i];
                description = description.replace("{var" + (i + 1) + "}", var);
            }
            metal.put("medal_description", description);
        }
    }

    /**
     * 查询指定用户的勋章 ID 与显示顺序
     * 调用方式：传入 userId，返回该用户所有勋章的 medal_id 和 display_order 列表，按 display_order 升序排列
     *
     * @param userId 用户 ID
     * @return 勋章 ID 与显示顺序列表
     * @throws ServiceException 查询失败时抛出
     */
    public List<JSONObject> getUserMedalIdAndOrder(final String userId) throws ServiceException {
        try {
            Query query = new Query()
                    .setFilter(new PropertyFilter("user_id", FilterOperator.EQUAL, userId))
                    .addSort("display_order", SortDirection.ASCENDING);
            List<JSONObject> userMedals = userMedalRepository.getList(query);
            List<JSONObject> result = new ArrayList<>();
            for (JSONObject userMedal : userMedals) {
                JSONObject item = new JSONObject();
                item.put("medal_id", userMedal.optString("medal_id"));
                item.put("display_order", userMedal.optInt("display_order", 0));
                result.add(item);
            }
            return result;
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to get medal id and order for user [" + userId + "]", e);
            throw new ServiceException("Failed to get medal id and order");
        }
    }

    /**
     * 查询所有勋章列表
     * 调用方式：不需要任何参数，直接返回系统中定义的所有勋章列表
     *
     * @return 勋章列表
     * @throws ServiceException 查询失败时抛出
     */
    public List<JSONObject> getAllMedals() throws ServiceException {
        try {
            Query query = new Query();
            return medalRepository.getList(query);
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to list all medals", e);
            throw new ServiceException("Failed to list all medals");
        }
    }

    /**
     * 分页查询勋章列表
     * 调用方式：传入 page（从 1 开始）、pageSize，返回对应页的勋章列表
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 勋章列表
     * @throws ServiceException 查询失败时抛出
     */
    public List<JSONObject> getAllMedalsPaged(final int page, final int pageSize) throws ServiceException {
        try {
            Query query = new Query()
                    .setCurrentPageNum(page)
                    .setPageSize(pageSize)
                    .setPageCount(1);
            return medalRepository.getList(query);
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to list medals paged", e);
            throw new ServiceException("Failed to list medals paged");
        }
    }

    /**
     * 增加勋章
     * 调用方式：传入勋章名称、类型、描述、属性，系统自动计算勋章 ID（medal_id 自增），创建一条新的勋章记录
     *
     * @param medalName        勋章名称
     * @param medalType        勋章类型
     * @param medalDescription 勋章描述
     * @param medalAttr        勋章属性 JSON 字符串或配置串
     * @return 新增记录的主键 oId
     * @throws ServiceException 新增失败时抛出
     */
    public String addMedal(final String medalName,
                           final String medalType,
                           final String medalDescription,
                           final String medalAttr) throws ServiceException {
        Transaction transaction = medalRepository.beginTransaction();
        try {
            Query nameQuery = new Query()
                    .setFilter(new PropertyFilter("medal_name", FilterOperator.EQUAL, medalName));
            List<JSONObject> existByName = medalRepository.getList(nameQuery);
            if (!existByName.isEmpty()) {
                transaction.commit();
                throw new ServiceException("勋章名称已存在：" + medalName);
            }

            Query allQuery = new Query();
            List<JSONObject> allMedals = medalRepository.getList(allQuery);
            int maxMedalId = -1;
            for (JSONObject medal : allMedals) {
                String idStr = medal.optString("medal_id", "-1");
                try {
                    int id = Integer.parseInt(idStr);
                    if (id > maxMedalId) {
                        maxMedalId = id;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            String nextMedalId = String.valueOf(maxMedalId + 1);

            JSONObject medal = new JSONObject();
            medal.put("medal_id", nextMedalId);
            medal.put("medal_name", medalName);
            medal.put("medal_type", medalType);
            medal.put("medal_description", medalDescription);
            medal.put("medal_attr", medalAttr);
            String oId = medalRepository.add(medal);
            transaction.commit();
            return oId;
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.log(Level.ERROR, "Failed to add medal [" + medalName + "]", e);
            throw new ServiceException("Failed to add medal");
        }
    }

    /**
     * 删除勋章
     * 调用方式：传入勋章 ID，删除 medal 表中对应记录，并可选删除所有用户对应的 user_medal 记录
     *
     * @param medalId 勋章唯一 ID
     * @throws ServiceException 删除失败时抛出
     */
    public void deleteMedal(final String medalId) throws ServiceException {
        Transaction transaction = medalRepository.beginTransaction();
        try {
            Query medalQuery = new Query()
                    .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
            List<JSONObject> medals = medalRepository.getList(medalQuery);
            for (JSONObject medal : medals) {
                String oId = medal.optString("oId");
                if (oId != null && !oId.isEmpty()) {
                    medalRepository.remove(oId);
                }
            }
            Query userMedalQuery = new Query()
                    .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
            List<JSONObject> userMedals = userMedalRepository.getList(userMedalQuery);
            for (JSONObject userMedal : userMedals) {
                String oId = userMedal.optString("oId");
                if (oId != null && !oId.isEmpty()) {
                    userMedalRepository.remove(oId);
                }
            }
            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.log(Level.ERROR, "Failed to delete medal [" + medalId + "]", e);
            throw new ServiceException("Failed to delete medal");
        }
    }

    /**
     * 修改已有的勋章信息
     * 调用方式：传入勋章 ID 以及新的名称、类型、描述、属性，更新 medal 表中对应记录
     *
     * @param medalId          勋章唯一 ID
     * @param medalName        勋章名称
     * @param medalType        勋章类型
     * @param medalDescription 勋章描述
     * @param medalAttr        勋章属性 JSON 字符串
     * @throws ServiceException 更新失败时抛出
     */
    public void updateMedal(final String medalId,
                            final String medalName,
                            final String medalType,
                            final String medalDescription,
                            final String medalAttr) throws ServiceException {
        try {
            Query nameQuery = new Query()
                    .setFilter(new PropertyFilter("medal_name", FilterOperator.EQUAL, medalName));
            List<JSONObject> existByName = medalRepository.getList(nameQuery);
            for (JSONObject medal : existByName) {
                String existMedalId = medal.optString("medal_id");
                if (!medalId.equals(existMedalId)) {
                    throw new ServiceException("勋章名称已存在：" + medalName);
                }
            }
            Query medalQuery = new Query()
                    .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
            List<JSONObject> medals = medalRepository.getList(medalQuery);
            for (JSONObject medal : medals) {
                String oId = medal.optString("oId");
                if (oId == null || oId.isEmpty()) {
                    continue;
                }
                medal.put("medal_name", medalName);
                medal.put("medal_type", medalType);
                medal.put("medal_description", medalDescription);
                medal.put("medal_attr", medalAttr);
                Transaction transaction = medalRepository.beginTransaction();
                medalRepository.update(oId, medal);
                transaction.commit();
            }
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to update medal [" + medalId + "]", e);
            throw new ServiceException("Failed to update medal");
        }
    }

    /**
     * 赋予某个用户指定 id 的勋章
     * 调用方式：传入 userId、medalId 和过期时间戳，如果用户已拥有该勋章则更新过期时间，如果没有则新增 user_medal 记录
     *
     * @param userId     用户 ID
     * @param medalId    勋章 ID
     * @param expireTime 过期时间（毫秒时间戳，0 表示永久）
     * @throws ServiceException 操作失败时抛出
     */
    public void grantMedalToUser(final String userId,
                                 final String medalId,
                                 final long expireTime,
                                 final String data) throws ServiceException {
        Transaction transaction = userMedalRepository.beginTransaction();
        try {
            Query query = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                            new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId)
                    ));
            JSONObject existing = userMedalRepository.getFirst(query);
            if (existing != null && existing.length() > 0) {
                String oId = existing.optString("oId");
                existing.put("expire_time", expireTime);
                existing.put("data", data);
                userMedalRepository.update(oId, existing);
            } else {
                Query allMedalsQuery = new Query()
                        .setFilter(new PropertyFilter("user_id", FilterOperator.EQUAL, userId));
                List<JSONObject> allUserMedals = userMedalRepository.getList(allMedalsQuery);
                int maxOrder = 0;
                for (JSONObject medal : allUserMedals) {
                    int order = medal.optInt("display_order", 0);
                    if (order > maxOrder) {
                        maxOrder = order;
                    }
                }
                int nextOrder = maxOrder + 1;
                JSONObject userMedal = new JSONObject();
                userMedal.put("user_id", userId);
                userMedal.put("medal_id", medalId);
                userMedal.put("data", data);
                userMedal.put("expire_time", expireTime);
                userMedal.put("display", true);
                userMedal.put("display_order", nextOrder);
                userMedalRepository.add(userMedal);
            }
            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.log(Level.ERROR, "Failed to grant medal [" + medalId + "] to user [" + userId + "]", e);
            throw new ServiceException("Failed to grant medal to user");
        }
    }

    /**
     * 取消赋予某个用户指定 id 的勋章
     * 调用方式：传入 userId 和 medalId，删除 user_medal 表中对应记录
     *
     * @param userId  用户 ID
     * @param medalId 勋章 ID
     * @throws ServiceException 操作失败时抛出
     */
    public void revokeMedalFromUser(final String userId,
                                    final String medalId) throws ServiceException {
        try {
            Query query = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                            new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId)
                    ));
            List<JSONObject> userMedals = userMedalRepository.getList(query);
            for (JSONObject userMedal : userMedals) {
                String oId = userMedal.optString("oId");
                if (oId != null && !oId.isEmpty()) {
                    Transaction transaction = userMedalRepository.beginTransaction();
                    userMedalRepository.remove(oId);
                    transaction.commit();
                }
            }
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to revoke medal [" + medalId + "] from user [" + userId + "]", e);
            throw new ServiceException("Failed to revoke medal from user");
        }
    }

    /**
     * 设置用户某个勋章是否在主页展示
     * 调用方式：传入 userId、medalId 和 display 布尔值，更新 user_medal.display 字段
     *
     * @param userId  用户 ID
     * @param medalId 勋章 ID
     * @param display 是否展示
     * @throws ServiceException 操作失败时抛出
     */
    public void setUserMedalDisplay(final String userId,
                                    final String medalId,
                                    final boolean display) throws ServiceException {
        Transaction transaction = userMedalRepository.beginTransaction();
        try {
            Query query = new Query()
                    .setFilter(CompositeFilterOperator.and(
                            new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                            new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId)
                    ));
            JSONObject existing = userMedalRepository.getFirst(query);
            if (existing == null || existing.length() == 0) {
                transaction.commit();
                return;
            }
            String oId = existing.optString("oId");
            existing.put("display", display);
            userMedalRepository.update(oId, existing);
            transaction.commit();
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            LOGGER.log(Level.ERROR, "Failed to set display for medal [" + medalId + "] of user [" + userId + "]", e);
            throw new ServiceException("Failed to set user medal display");
        }
    }

    /**
     * 搜索指定勋章信息
     * 调用方式：传入关键词 keyword，在 medal_id、medal_name、medal_description 中进行模糊匹配，返回匹配到的勋章列表
     *
     * @param keyword 搜索关键词
     * @return 勋章列表
     * @throws ServiceException 查询失败时抛出
     */
    public List<JSONObject> searchMedals(final String keyword) throws ServiceException {
        try {
            String likeValue = "%" + keyword + "%";
            Query query = new Query()
                    .setFilter(CompositeFilterOperator.or(
                            new PropertyFilter("medal_id", FilterOperator.LIKE, likeValue),
                            new PropertyFilter("medal_name", FilterOperator.LIKE, likeValue),
                            new PropertyFilter("medal_description", FilterOperator.LIKE, likeValue)
                    ));
            return medalRepository.getList(query);
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to search medals by keyword [" + keyword + "]", e);
            throw new ServiceException("Failed to search medals");
        }
    }

    /**
     * 根据勋章名称精确查找勋章
     * 调用方式：传入完整的勋章名称 medalName，只在 medal_name 字段上做等值匹配，返回第一条匹配记录
     *
     * @param medalName 勋章名称（精确）
     * @return 勋章 JSON，如果不存在则返回 null
     * @throws ServiceException 查询失败时抛出
     */
    public JSONObject getMedalByExactName(final String medalName) throws ServiceException {
        try {
            Query query = new Query()
                    .setFilter(new PropertyFilter("medal_name", FilterOperator.EQUAL, medalName));
            List<JSONObject> medals = medalRepository.getList(query);
            if (medals == null || medals.isEmpty()) {
                return null;
            }
            return medals.get(0);
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to get medal by exact name [" + medalName + "]", e);
            throw new ServiceException("Failed to get medal by exact name");
        }
    }

    /**
     * 根据勋章 ID 精确查找勋章
     * 调用方式：传入 medal_id，只在 medal_id 字段上做等值匹配，返回第一条匹配记录
     *
     * @param medalId 勋章 ID（精确）
     * @return 勋章 JSON，如果不存在则返回 null
     * @throws ServiceException 查询失败时抛出
     */
    public JSONObject getMedalById(final String medalId) throws ServiceException {
        try {
            Query query = new Query()
                    .setFilter(new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId));
            List<JSONObject> medals = medalRepository.getList(query);
            if (medals == null || medals.isEmpty()) {
                return null;
            }
            return medals.get(0);
        } catch (RepositoryException e) {
            LOGGER.log(Level.ERROR, "Failed to get medal by id [" + medalId + "]", e);
            throw new ServiceException("Failed to get medal by id");
        }
    }

    /**
     * 从云存档迁移旧勋章数据到 medal / user_medal 表
     * 调用方式：在外部先清空 medal 和 user_medal 表，然后调用本方法完成一次全量迁移
     *
     * 迁移规则：
     * 1. 勋章定义：
     *    - medal_id：字符串形式的自增 ID，从 0 开始；
     *    - medal_name：旧字段 name；
     *    - medal_type：固定为 "cloud"；
     *    - medal_description：旧字段 description；
     *    - medal_attr：旧字段 attr；
     *    - 同名勋章存在多条时，以 oId 最大的那条为准，其余同名记录忽略。
     * 2. 用户拥有关系：
     *    - user_id：来自 cloud.userId；
     *    - medal_id：上面生成的 medal_id；
     *    - expire_time：如果旧 JSON 中有 expireDate，则转换为毫秒时间戳，否则为 0；
     *    - display：旧 JSON 中 enabled 字段，缺省视为 true；
     *    - display_order：按该用户本次迁移到的勋章顺序，从 1 开始递增。
     *
     * @throws ServiceException 迁移失败时抛出
     */
    public void migrateCloudMedals() throws ServiceException {
        Transaction transaction = medalRepository.beginTransaction();
        MEDAL_MIGRATION_PROGRESS.status = "RUNNING";
        MEDAL_MIGRATION_PROGRESS.message = "";
        MEDAL_MIGRATION_PROGRESS.current = 0;
        try {
            Query medalQuery = new Query();
            List<JSONObject> existingMedals = medalRepository.getList(medalQuery);
            Map<String, JSONObject> latestMedalByName = new HashMap<>();
            int currentMaxMedalId = -1;
            for (JSONObject medal : existingMedals) {
                String name = medal.optString("medal_name");
                String medalIdStr = medal.optString("medal_id", "-1");
                int medalIdInt;
                try {
                    medalIdInt = Integer.parseInt(medalIdStr);
                } catch (NumberFormatException e) {
                    medalIdInt = -1;
                }
                if (medalIdInt > currentMaxMedalId) {
                    currentMaxMedalId = medalIdInt;
                }
                if (!latestMedalByName.containsKey(name)) {
                    latestMedalByName.put(name, medal);
                } else {
                    JSONObject existing = latestMedalByName.get(name);
                    String existingOId = existing.optString("oId", "0");
                    String currentOId = medal.optString("oId", "0");
                    if (currentOId.compareTo(existingOId) > 0) {
                        latestMedalByName.put(name, medal);
                    }
                }
            }
            Query cloudQuery = new Query()
                    .setFilter(new PropertyFilter("gameId", FilterOperator.EQUAL, CloudService.SYS_MEDAL));
            List<JSONObject> cloudMedals = cloudRepository.getList(cloudQuery);
            int totalTasks = 0;
            for (JSONObject cloudRow : cloudMedals) {
                String dataStr = cloudRow.optString("data", "");
                try {
                    JSONObject data = new JSONObject(dataStr);
                    JSONArray list = data.optJSONArray("list");
                    if (list != null) {
                        totalTasks += list.length();
                    }
                } catch (Exception ignore) {
                }
            }
            MEDAL_MIGRATION_PROGRESS.total = totalTasks;
            MEDAL_MIGRATION_PROGRESS.current = 0;
            Map<String, Integer> userOrderCounter = new HashMap<>();
            for (JSONObject cloudRow : cloudMedals) {
                String userId = cloudRow.optString("userId");
                String dataStr = cloudRow.optString("data", "");
                JSONObject data;
                try {
                    data = new JSONObject(dataStr);
                } catch (Exception e) {
                    continue;
                }
                JSONArray list = data.optJSONArray("list");
                if (list == null) {
                    continue;
                }
                int orderCounter = userOrderCounter.getOrDefault(userId, 0);
                for (int i = 0; i < list.length(); i++) {
                    JSONObject oldMedal = list.optJSONObject(i);
                    MEDAL_MIGRATION_PROGRESS.current++;
                    if (oldMedal == null) {
                        continue;
                    }
                    String name = oldMedal.optString("name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    String description = oldMedal.optString("description", "");
                    String attr = oldMedal.optString("attr", "");
                    boolean enabled = oldMedal.optBoolean("enabled", true);
                    long expireTime = 0L;
                    String expireDateStr = oldMedal.optString("expireDate", null);
                    if (expireDateStr != null && !expireDateStr.isEmpty()) {
                        try {
                            long millis = Dates.parseOrNull(expireDateStr, Dates.PATTERN_DATE).getTime();
                            expireTime = millis;
                        } catch (Exception e) {
                        }
                    }
                    JSONObject medalDef = latestMedalByName.get(name);
                    String medalId;
                    if (medalDef == null) {
                        currentMaxMedalId++;
                        medalId = String.valueOf(currentMaxMedalId);
                        JSONObject newMedal = new JSONObject();
                        newMedal.put("medal_id", medalId);
                        newMedal.put("medal_name", name);
                        newMedal.put("medal_type", "cloud");
                        newMedal.put("medal_description", description);
                        newMedal.put("medal_attr", attr);
                        String oId = medalRepository.add(newMedal);
                        newMedal.put("oId", oId);
                        latestMedalByName.put(name, newMedal);
                    } else {
                        medalId = medalDef.optString("medal_id");
                    }
                    Query userMedalQuery = new Query()
                            .setFilter(CompositeFilterOperator.and(
                                    new PropertyFilter("user_id", FilterOperator.EQUAL, userId),
                                    new PropertyFilter("medal_id", FilterOperator.EQUAL, medalId)
                            ));
                    JSONObject existingUserMedal = userMedalRepository.getFirst(userMedalQuery);
                    if (existingUserMedal != null && existingUserMedal.length() > 0) {
                        String oId = existingUserMedal.optString("oId");
                        existingUserMedal.put("expire_time", expireTime);
                        existingUserMedal.put("display", enabled);
                        if (!existingUserMedal.has("data") || existingUserMedal.isNull("data")) {
                            existingUserMedal.put("data", "");
                        }
                        userMedalRepository.update(oId, existingUserMedal);
                    } else {
                        orderCounter++;
                        JSONObject userMedal = new JSONObject();
                        userMedal.put("user_id", userId);
                        userMedal.put("medal_id", medalId);
                        userMedal.put("data", "");
                        userMedal.put("expire_time", expireTime);
                        userMedal.put("display", enabled);
                        userMedal.put("display_order", orderCounter);
                        userMedalRepository.add(userMedal);
                    }
                }
                userOrderCounter.put(userId, orderCounter);
            }
            transaction.commit();
            LOGGER.info("Finished migrating medals from cloud, total tasks: " + totalTasks);
            MEDAL_MIGRATION_PROGRESS.status = "DONE";
            MEDAL_MIGRATION_PROGRESS.message = "OK";
        } catch (RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            MEDAL_MIGRATION_PROGRESS.status = "FAILED";
            MEDAL_MIGRATION_PROGRESS.message = e.getMessage();
            LOGGER.log(Level.ERROR, "Failed to migrate cloud medals", e);
            throw new ServiceException("Failed to migrate cloud medals");
        }
    }
}
