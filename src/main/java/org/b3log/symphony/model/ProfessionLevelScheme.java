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
package org.b3log.symphony.model;

/** 职业等级方案版本字段。 */
public final class ProfessionLevelScheme {

    public static final String PROFESSION_LEVEL_SCHEME = "profession_level_scheme";
    public static final String PROFESSION_ID = "professionId";
    public static final String PROFESSION_REVISION_ID = "professionRevisionId";
    public static final String REVISION_NO = "revisionNo";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String STATUS = "status";
    public static final String MIGRATION_POLICY = "migrationPolicy";
    public static final String REWARD_MIGRATION_POLICY = "rewardMigrationPolicy";
    public static final String MIGRATION_CONFIG_JSON = "migrationConfigJson";
    public static final String EFFECTIVE_FROM = "effectiveFrom";
    public static final String EFFECTIVE_TO = "effectiveTo";
    public static final String CREATED_BY = "createdBy";
    public static final String CREATED_AT = "createdAt";
    public static final String PUBLISHED_BY = "publishedBy";
    public static final String PUBLISHED_AT = "publishedAt";

    private ProfessionLevelScheme() {
    }
}
