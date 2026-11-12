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

import java.util.List;

/** 稳定素材目录只允许本地已发布的资源，价格和上下架状态另由数据库维护。 */
public final class ApiV3AppearanceCatalog {
    public record Product(String id, String kind, String styleId, String name, String verb) { }

    public static final List<Product> PRODUCTS = List.of(
            new Product("mount-paipai-scooter", "mount", "paipai-scooter", "派派小电驴", "骑着"),
            new Product("mount-paper-plane", "mount", "paper-plane", "摸鱼纸飞机", "乘着"),
            new Product("mount-deepsea-submarine", "mount", "deepsea-submarine", "深海潜艇", "开着"),
            new Product("bubble-sea-soda", "bubble", "sea-soda", "海盐汽水", ""),
            new Product("bubble-sunset-letter", "bubble", "sunset-letter", "落日邮笺", ""),
            new Product("bubble-star-route", "bubble", "star-route", "星夜航线", ""),
            new Product("frame-tide", "frame", "tide", "潮汐", ""),
            new Product("frame-paipai-stamp", "frame", "paipai-stamp", "派派邮票", ""),
            new Product("frame-star-ring", "frame", "star-ring", "星环", ""));

    private ApiV3AppearanceCatalog() { }

    public static Product find(String id) {
        return PRODUCTS.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    public static Product style(String kind, String id) {
        return PRODUCTS.stream().filter(item -> item.kind().equals(kind) && item.styleId().equals(id)).findFirst().orElse(null);
    }

    /** 按精确时长续期；同键恢复不再执行此计算。 */
    public static long extend(long now, long previous, int days) {
        if (days != 7 && days != 30) throw ApiV3WriteInputs.invalid();
        try {
            final long expiry = Math.addExact(Math.max(now, previous), Math.multiplyExact((long) days, 86400000L));
            if (expiry > 8640000000000000L) throw ApiV3WriteInputs.invalid();
            return expiry;
        } catch (ArithmeticException failure) {
            throw ApiV3WriteInputs.invalid();
        }
    }
}
