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

import org.b3log.latke.Keys;
import org.b3log.symphony.model.Domain;
import org.b3log.symphony.model.Tag;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Set;

public class ApiV3DiscoveryQueryServiceTest {
    @Test
    public void searchTreatsLikeMetacharactersLiterally() {
        Assert.assertEquals(ApiV3DiscoveryQueryService.escapeLike("100%_done!"), "100!%!_done!!");
        Assert.assertEquals(ApiV3DiscoveryQueryService.escapeLike("a'b\\c中文"), "a'b\\c中文");
    }

    @Test
    public void tagDtoCannotLeakCssAdsOrAuditFields() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "1").put(Tag.TAG_TITLE, "Java")
                .put(Tag.TAG_DESCRIPTION, "description").put(Tag.TAG_STATUS, 1)
                .put(Tag.TAG_CSS, "private-css").put(Tag.TAG_AD, "private-ad").put("token", "secret");
        final JSONObject dto = ApiV3DiscoveryQueryService.tagDto(raw);
        Assert.assertEquals(dto.keySet(), Set.of("id", "title", "uri", "description", "iconURL", "articleCount", "followerCount"));
        Assert.assertFalse(dto.toString().contains("secret"));
        Assert.assertEquals(raw.optString(Tag.TAG_CSS), "private-css");
    }

    @Test
    public void domainDtoCannotLeakCssOrControlFields() {
        final JSONObject raw = new JSONObject().put(Keys.OBJECT_ID, "2").put(Domain.DOMAIN_TITLE, "编程")
                .put(Domain.DOMAIN_STATUS, 1).put(Domain.DOMAIN_CSS, "private-css").put("apiKey", "secret");
        final JSONObject dto = ApiV3DiscoveryQueryService.domainDto(raw);
        Assert.assertEquals(dto.keySet(), Set.of("id", "title", "uri", "description", "iconURL", "tagCount"));
        Assert.assertFalse(dto.toString().contains("secret"));
    }
}
