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
 * Point transfer source application snapshot.
 *
 * @param appId     source application id
 * @param appName   source application name
 * @param scene     source scene
 * @param requestId source request id
 */
public record PointtransferSource(String appId, String appName, String scene, String requestId) {

    /**
     * Empty source for legacy/internal point transfers.
     */
    public static final PointtransferSource EMPTY = new PointtransferSource("", "", "", "");

    /**
     * Normalizes nullable values.
     */
    public PointtransferSource {
        appId = null == appId ? "" : appId;
        appName = null == appName ? "" : appName;
        scene = null == scene ? "" : scene;
        requestId = null == requestId ? "" : requestId;
    }
}
