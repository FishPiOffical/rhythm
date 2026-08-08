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
import org.b3log.latke.repository.FilterOperator;
import org.b3log.latke.repository.PropertyFilter;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Profession;
import org.b3log.symphony.repository.ProfessionRepository;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 导入前校验文件结构并明确显示代号冲突。 */
@Service
public class ProfessionConfigImportPreviewService {

    private static final int MAX_DOCUMENT_LENGTH = 16_000_000;

    @Inject private ProfessionConfigDocumentValidator documentValidator;
    @Inject private ProfessionRepository professionRepository;

    public JSONObject preview(final String configJson) throws RepositoryException {
        final JSONObject document;
        try {
            document = document(configJson);
            documentValidator.validate(document);
        } catch (final IllegalArgumentException | JSONException e) {
            return invalid(e.getMessage());
        }
        return conflicts(document.getJSONArray("professions"));
    }

    private JSONObject document(final String configJson) {
        if (null == configJson || configJson.isBlank() || configJson.length() > MAX_DOCUMENT_LENGTH) {
            throw new IllegalArgumentException("配置文件长度不合法");
        }
        return new JSONObject(configJson);
    }

    private JSONObject conflicts(final JSONArray professions) throws RepositoryException {
        final JSONArray ready = new JSONArray();
        final JSONArray conflicts = new JSONArray();
        for (final Object item : professions) {
            final String code = ((JSONObject) item).getString(Profession.CODE);
            final JSONObject value = new JSONObject().put(Profession.CODE, code);
            if (exists(code)) {
                conflicts.put(value.put("reason", "职业代号已存在"));
            } else {
                ready.put(value);
            }
        }
        return new JSONObject().put("valid", true).put("total", professions.length()).put("ready", ready)
                .put("conflicts", conflicts);
    }

    private boolean exists(final String code) throws RepositoryException {
        final Query query = new Query().setFilter(new PropertyFilter(Profession.CODE, FilterOperator.EQUAL, code));
        return null != professionRepository.getFirst(query);
    }

    private JSONObject invalid(final String message) {
        return new JSONObject().put("valid", false).put("error", message).put("total", 0)
                .put("ready", new JSONArray()).put("conflicts", new JSONArray());
    }
}
