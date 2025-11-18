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
package org.b3log.symphony.util;

import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.repository.Query;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.json.JSONObject;
import java.util.List;

public class PointtransferSyncTool {

    public static void syncAllHistoryToRedc() throws Exception {
        // 获取Bean
        PointtransferRepository pointtransferRepository = BeanManager.getInstance().getReference(PointtransferRepository.class);

        // 1. 查询主表所有数据
        Query query = new Query(); // 不设置分页，查全部
        List<JSONObject> records = pointtransferRepository.getList(query);

        if (records == null || records.isEmpty()) {
            System.out.println("主表无数据，无需同步。");
            return;
        }

        int count = 0;
        for (JSONObject record : records) {
            System.out.println("正在处理数据：" + record);
            String oId = record.optString("oId");
            String fromId = record.optString("fromId");
            String toId = record.optString("toId");

            // 插入fromId冗余
            try {
                PointtransferRedcRepository.addRecord(fromId, oId);
            } catch (Exception e) {
                // 已存在或其他异常，忽略或记录日志
                e.printStackTrace();
            }
            // 插入toId冗余
            try {
                PointtransferRedcRepository.addRecord(toId, oId);
            } catch (Exception e) {
                // 已存在或其他异常，忽略或记录日志
                e.printStackTrace();
            }
            count += 2;
        }

        System.out.println("历史数据同步完成！共插入冗余记录数：" + count);
    }
}
