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

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.json.JSONObject;

/** 长篇阅读服务入口。 */
@Service
public class LongArticleReadService {

    @Inject
    private LongArticleReadCaptureService captureService;

    @Inject
    private LongArticleReadQueryService queryService;

    @Inject
    private LongArticleReadSettlementService settlementService;

    public void record(final LongArticleReadCaptureRequest request) {
        captureService.record(request);
    }

    public JSONObject getStat(final String articleId) {
        return queryService.getStat(articleId);
    }

    public void settleAll() {
        settlementService.settleAll();
    }

    public void settle(final String articleId) {
        settlementService.settle(articleId);
    }
}
