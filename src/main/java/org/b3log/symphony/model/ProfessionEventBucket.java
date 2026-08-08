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

/** 高频职业事件聚合桶字段。 */
public final class ProfessionEventBucket {

    public static final String PROFESSION_EVENT_BUCKET = "profession_event_bucket";
    public static final String BUCKET_KEY = "bucketKey";
    public static final String TRIGGER_TYPE = "triggerType";
    public static final String AUTOMATION_REVISION_ID = "automationRevisionId";
    public static final String SUBJECT_USER_ID = "subjectUserId";
    public static final String WINDOW_START = "windowStart";
    public static final String DIMENSION_KEY = "dimensionKey";
    public static final String DIMENSION_HASH = "dimensionHash";
    public static final String EVENT_COUNT = "eventCount";
    public static final String NUMERIC_SUM = "numericSum";
    public static final String DISTINCT_COUNT = "distinctCount";
    public static final String SETTLEMENT_SEQUENCE = "settlementSequence";
    public static final String STATUS = "status";
    public static final String LAST_OCCURRED_AT = "lastOccurredAt";
    public static final String NEXT_SETTLE_AT = "nextSettleAt";
    public static final String LOCKED_AT = "lockedAt";
    public static final String PROCESSING_ATTEMPT = "processingAttempt";
    public static final String NEXT_RETRY_AT = "nextRetryAt";
    public static final String ERROR_CODE = "errorCode";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private ProfessionEventBucket() {
    }
}
