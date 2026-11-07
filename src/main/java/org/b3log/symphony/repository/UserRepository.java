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
import org.b3log.latke.model.User;
import org.b3log.latke.repository.*;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.cache.UserCache;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.util.Sessions;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * User repository.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 2.2.0.0, Jun 23, 2020
 * @since 0.2.0
 */
@Repository
public class UserRepository extends AbstractRepository {

    /**
     * User cache.
     */
    @Inject
    private UserCache userCache;

    /**
     * Per-user locks keyed by user id to serialize updates for the same user.
     */
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    /**
     * Gets (or creates) a lock for the specified user id.
     *
     * @param id user id
     * @return the lock associated with the user
     */
    private ReentrantLock getUserLock(final String id) {
        return userLocks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    /**
     * Public constructor.
     */
    public UserRepository() {
        super(User.USER);
    }

    public JSONObject getForUpdate(final String id) throws RepositoryException {
        final List<JSONObject> values = select("SELECT * FROM `" + getName() + "` WHERE `oId`=? FOR UPDATE", id);
        return values.isEmpty() ? null : values.getFirst();
    }

    /** Settings must not publish an uncommitted user into UserCache/Sessions. */
    public void updateSettingsInCurrentTransaction(final String id, final JSONObject changes) throws RepositoryException {
        updateColumnsInCurrentTransaction(id, changes);
    }

    /** Exact columns only, with no uncommitted user/cache publication. */
    public void updateColumnsInCurrentTransaction(final String id, final JSONObject changes) throws RepositoryException {
        if (!hasTransactionBegun()) throw new IllegalStateException("user update transaction required");
        super.update(id, changes, changes.keySet().toArray(new String[0]));
    }

    public void evictSettingsCaches(final String id) {
        final JSONObject cached = userCache.getUser(id);
        if (null != cached) userCache.removeUser(cached);
        Sessions.invalidate(id);
    }

    public void evictUserNameCaches(final String id, final String oldName) {
        if (hasTransactionBegun()) throw new IllegalStateException("username cache invalidation before transaction end");
        userCache.removeUser(new JSONObject().put(Keys.OBJECT_ID, id).put(User.USER_NAME, oldName));
        Sessions.invalidate(id);
    }

    /** Refresh only durable data, serialized with other settings writers by the owner row. */
    public void refreshSettingsCache(final String id) throws RepositoryException {
        if (hasTransactionBegun()) throw new IllegalStateException("settings cache refresh before transaction end");
        final Transaction tx = beginTransaction();
        try {
            final JSONObject row = getForUpdate(id);
            if (null != row) userCache.putUser(row);
            tx.commit();
        } catch (final RepositoryException | RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public JSONObject get(final String id) throws RepositoryException {
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("publication transaction required");
            return getForUpdate(id);
        }
        JSONObject ret = userCache.getUser(id);
        if (null != ret) {
            return ret;
        }

        ret = super.get(id);

        if (null == ret) {
            return null;
        }

        userCache.putUser(ret);

        return ret;
    }

    @Override
    public void update(final String id, final JSONObject user, final String... propertyNames) throws RepositoryException {
        if (ArticlePublicationCaches.active()) {
            if (!hasTransactionBegun()) throw new IllegalStateException("publication transaction required");
            ArticlePublicationCaches.afterTransaction("user:" + id, () -> {
                try { refreshSettingsCache(id); }
                catch (final RepositoryException | RuntimeException e) { evictSettingsCaches(id); }
            });
            super.update(id, user, propertyNames);
            return;
        }
        final ReentrantLock lock = getUserLock(id);
        lock.lock();
        try {
            final JSONObject old = get(id);
            if (null == old) {
                return;
            }

            userCache.removeUser(old);
            super.update(id, user, propertyNames);
            user.put(Keys.OBJECT_ID, id);
            userCache.putUser(user);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets a user by the specified name.
     *
     * @param name the specified name
     * @return user, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByName(final String name) throws RepositoryException {
        JSONObject ret = userCache.getUserByName(name);
        if (null != ret) {
            return ret;
        }

        final Query query = new Query().setPageCount(1).setFilter(new PropertyFilter(User.USER_NAME, FilterOperator.EQUAL, name));
        ret = getFirst(query);
        if (null == ret) {
            return null;
        }
        userCache.putUser(ret);
        return ret;
    }

    /**
     * Gets a user by the specified email.
     *
     * @param email the specified email
     * @return user, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByEmail(final String email) throws RepositoryException {
        final Query query = new Query().setPageCount(1).setFilter(new PropertyFilter(User.USER_EMAIL, FilterOperator.EQUAL, email.toLowerCase().trim()));
        return getFirst(query);
    }

    /**
     * Gets a user by the specified phone.
     * @param phone the specified phone
     * @return user, returns {@code null} if not found
     * @throws RepositoryException repository exception
     */
    public JSONObject getByPhone(final String phone) throws RepositoryException {
        final Query query = new Query().setPageCount(1).setFilter(new PropertyFilter("userPhone", FilterOperator.EQUAL, phone));
        return getFirst(query);
    }

    /**
     * Gets the administrators.
     *
     * @return administrators, returns an empty list if not found or error
     * @throws RepositoryException repository exception
     */
    public List<JSONObject> getAdmins() throws RepositoryException {
        List<JSONObject> ret = userCache.getAdmins();
        if (ret.isEmpty()) {
            final Query query = new Query().setFilter(
                    new PropertyFilter(User.USER_ROLE, FilterOperator.EQUAL, Role.ROLE_ID_C_ADMIN)).setPageCount(1)
                    .addSort(Keys.OBJECT_ID, SortDirection.ASCENDING);
            ret = getList(query);
            userCache.putAdmins(ret);
        }

        return ret;
    }

    /**
     * Gets the anonymous user.
     *
     * @return anonymous user
     * @throws RepositoryException repository exception
     */
    public JSONObject getAnonymousUser() throws RepositoryException {
        return getByName(UserExt.ANONYMOUS_USER_NAME);
    }
}
