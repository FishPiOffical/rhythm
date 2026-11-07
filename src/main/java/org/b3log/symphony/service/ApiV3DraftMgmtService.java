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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ArticleDraft;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.b3log.symphony.util.ApiV3DraftInputs;
import org.b3log.symphony.util.ApiV3DraftDto;
import org.json.JSONObject;


/** Owned draft writes using the original draft rules and the v3 atomic replay boundary. */
@Service
public class ApiV3DraftMgmtService {
    @Inject private ArticleDraftMgmtService draftMgmtService;
    @Inject private ApiV3IdempotencyMgmtService idempotencyMgmtService;

    public JSONObject save(final String userId, final String id, final String key, final JSONObject body) throws Exception {
        final JSONObject request = ApiV3DraftInputs.normalize(body);
        if (null != id) request.put(ArticleDraft.ARTICLE_DRAFT_ID, id);
        return idempotencyMgmtService.execute(userId, null == id ? "drafts.create" : "drafts.update:" + id, key, request,
                () -> ApiV3DraftDto.from(saveInTransaction(userId, request), false));
    }

    public JSONObject remove(final String userId, final String id, final String key) throws Exception {
        return idempotencyMgmtService.execute(userId, "drafts.delete:" + id, key, new JSONObject(), () -> {
            try { draftMgmtService.removeDraftInCurrentTransaction(userId, id); }
            catch (final ServiceException e) { throw new ApiV3Exception(404, "not_found", "草稿不存在或不可访问"); }
            return new JSONObject();
        });
    }

    private JSONObject saveInTransaction(final String userId, final JSONObject request) throws RepositoryException {
        try { return draftMgmtService.saveDraftInCurrentTransaction(userId, request); }
        catch (final ServiceException e) {
            if ("草稿不存在".equals(e.getMessage())) throw new ApiV3Exception(404, "not_found", "草稿不存在或不可访问");
            throw ApiV3WriteInputs.invalid();
        }
    }

}
