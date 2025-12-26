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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
        int threadCount = 30; // 可根据实际情况调整
        ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        Semaphore limiter = new Semaphore(threadCount);
        CountDownLatch latch = new CountDownLatch(records.size() * 2); // 每条记录2个插入任务
        AtomicInteger count = new AtomicInteger(0);
        for (JSONObject record : records) {
            String oId = record.optString("oId");
            String fromId = record.optString("fromId");
            String toId = record.optString("toId");
            // fromId冗余插入
            limiter.acquireUninterruptibly();
            executor.submit(() -> {
                try {
                    PointtransferRedcRepository.addRecordAsync(fromId, oId);
                    count.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                    limiter.release();
                }
            });
            // toId冗余插入
            limiter.acquireUninterruptibly();
            executor.submit(() -> {
                try {
                    PointtransferRedcRepository.addRecordAsync(toId, oId);
                    count.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                    limiter.release();
                }
            });
        }
        // 等待所有任务完成
        latch.await();
        executor.shutdown();
        System.out.println("历史数据同步完成！共插入冗余记录数：" + count.get());
    }
}
