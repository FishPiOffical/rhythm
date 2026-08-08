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

/** 职业可信来源事件字段。 */
public final class ProfessionSourceEvent {

    public static final String PROFESSION_SOURCE_EVENT = "profession_source_event";
    public static final String EVENT_KEY = "eventKey";
    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String SUBJECT_USER_ID = "subjectUserId";
    public static final String ACTOR_USER_ID = "actorUserId";
    public static final String TARGET_TYPE = "targetType";
    public static final String TARGET_ID = "targetId";
    public static final String OCCURRED_AT = "occurredAt";
    public static final String CAPTURED_AT = "capturedAt";
    public static final String PAYLOAD_JSON = "payloadJson";
    public static final String VISIBILITY_CLASS = "visibilityClass";
    public static final String REVERSAL_OF_EVENT_KEY = "reversalOfEventKey";
    public static final String SOURCE_SYSTEM = "sourceSystem";
    public static final String SOURCE_APP_ID = "sourceAppId";
    public static final String EXTERNAL_REQUEST_ID = "externalRequestId";
    public static final String STATUS = "status";
    public static final String PROCESSING_ATTEMPT = "processingAttempt";
    public static final String NEXT_RETRY_AT = "nextRetryAt";
    public static final String LOCKED_AT = "lockedAt";
    public static final String ERROR_CODE = "errorCode";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";

    private ProfessionSourceEvent() {
    }
}
