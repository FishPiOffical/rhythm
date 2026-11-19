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

import java.util.function.Supplier;
import org.b3log.latke.repository.Transaction;

/** 搭子接口统一在借连接处区分服务繁忙和业务失败。 */
public final class PartnerTransactions {
    private PartnerTransactions() { }

    public static Transaction begin(Supplier<Transaction> opener) {
        try {
            return opener.get();
        } catch (IllegalStateException failure) {
            // 当前 Latke 会记录原始 SQL 异常，但向调用方只保留这个事务初始化错误。
            if (!"Begin a transaction failed".equals(failure.getMessage())) throw failure;
            throw new ApiV3Exception(503, "partner_busy", "服务繁忙，请稍后再试");
        }
    }
}
