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

import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.FishGame;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ApiV3AdminFishGameInputTest {

    private static final String ICON = "https://example.test/icon.png";
    private static final String LEGACY_ICON = "https://example.test/legacy.png";

    private final FishGameValidationService validation = new FishGameValidationService();

    @Test
    public void acceptsEitherIconFieldWithoutEditingTheOriginalRequest() throws ServiceException {
        final JSONObject current = request().put(FishGame.ICON_URL, ICON);
        final JSONObject legacy = request().put(FishGame.LEGACY_ADMIN_ICON_URL, LEGACY_ICON);

        Assert.assertEquals(validate(current).getString(FishGame.ICON_URL), ICON);
        Assert.assertEquals(validate(legacy).getString(FishGame.ICON_URL), LEGACY_ICON);
        Assert.assertFalse(legacy.has(FishGame.ICON_URL));
        Assert.assertEquals(legacy.getString(FishGame.LEGACY_ADMIN_ICON_URL), LEGACY_ICON);
    }

    @Test
    public void canonicalIconTakesPrecedenceAndStillRequiresHttps() throws ServiceException {
        final JSONObject both = request().put(FishGame.ICON_URL, ICON).put(FishGame.LEGACY_ADMIN_ICON_URL, LEGACY_ICON);
        Assert.assertEquals(validate(both).getString(FishGame.ICON_URL), ICON);

        both.put(FishGame.ICON_URL, "");
        Assert.expectThrows(ServiceException.class, () -> validate(both));
        Assert.expectThrows(ServiceException.class,
                () -> validate(request().put(FishGame.LEGACY_ADMIN_ICON_URL, "http://example.test/icon.png")));
        Assert.expectThrows(ServiceException.class, () -> validate(request()));
    }

    private JSONObject validate(final JSONObject request) throws ServiceException {
        return validation.build(ApiV3AdminOpsService.fishGameAdminInput(request), "example-author", FishGame.STATUS_APPROVED);
    }

    private static JSONObject request() {
        return new JSONObject().put(FishGame.NAME, "示例鱼游")
                .put(FishGame.DESCRIPTION, "测试描述")
                .put(FishGame.URL, "https://example.test/game");
    }
}
