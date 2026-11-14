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
package org.b3log.symphony.repository;

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.cache.OptionCache;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;

/**
 * Option repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.2.1.1, Jun 6, 2019
 * @since 0.2.0
 */
@Repository
public class OptionRepository extends AbstractRepository {

    /**
     * Option cache.
     */
    @Inject
    private OptionCache optionCache;

    /**
     * Public constructor.
     */
    public OptionRepository() {
        super(Option.OPTION);
    }

    @Override
    public void remove(final String id) throws RepositoryException {
        super.remove(id);

        optionCache.removeOption(id);
    }

    @Override
    public JSONObject get(final String id) throws RepositoryException {
        if (ArticlePublicationCaches.active()) return getForUpdate(id);
        JSONObject ret = optionCache.getOption(id);
        if (null != ret) {
            return ret;
        }

        ret = super.get(id);
        if (null == ret) {
            return null;
        }

        optionCache.putOption(ret);

        return ret;
    }

    @Override
    public void update(final String id, final JSONObject option, final String... propertyNames) throws RepositoryException {
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("publication transaction required");
            ArticlePublicationCaches.afterTransaction("option:" + id, () -> optionCache.removeOption(id));
            super.update(id, option, propertyNames);
            return;
        }
        super.update(id, option, propertyNames);

        option.put(Keys.OBJECT_ID, id);
        optionCache.putOption(option);
    }

    public JSONObject getForUpdate(final String id) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("option transaction required");
        final var rows = select("SELECT * FROM `" + getName() + "` WHERE oId=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
