/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.b3log.symphony.model;

/**
 * External point adjustment request model keys.
 */
public final class ExternalPointRequest {

    public static final String EXTERNAL_POINT_REQUEST = "external_point_request";
    public static final String SOURCE_APP_ID = "sourceAppId";
    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_HASH = "requestHash";
    public static final String TRANSFER_ID = "transferId";
    public static final String CREATE_TIME = "createTime";

    private ExternalPointRequest() {
    }
}
