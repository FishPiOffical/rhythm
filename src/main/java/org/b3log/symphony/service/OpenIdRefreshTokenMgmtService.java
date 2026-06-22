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

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.OpenIdRefreshToken;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.OpenIdRefreshTokenRepository;
import org.b3log.symphony.util.OpenIdUtil;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OpenID refresh token management service.
 */
@Service
public class OpenIdRefreshTokenMgmtService {

    private static final Logger LOGGER = LogManager.getLogger(OpenIdRefreshTokenMgmtService.class);

    private static final String TOKEN_TYPE = "Bearer";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String EXPIRES_IN = "expires_in";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String REFRESH_EXPIRES_IN = "refresh_expires_in";
    private static final String SCOPE = "scope";
    private static final String TOKEN_TYPE_KEY = "token_type";

    private static final long DEFAULT_ACCESS_TOKEN_EXPIRES_SECONDS = TimeUnit.DAYS.toSeconds(7);
    private static final long DEFAULT_REFRESH_TOKEN_IDLE_EXPIRES_SECONDS = TimeUnit.DAYS.toSeconds(360);
    private static final long DEFAULT_REFRESH_TOKEN_MAX_EXPIRES_SECONDS = TimeUnit.DAYS.toSeconds(720);
    private static final long ACCESS_TOKEN_EXPIRES_SECONDS = getSeconds("openid.accessTokenExpires",
            DEFAULT_ACCESS_TOKEN_EXPIRES_SECONDS);
    private static final long REFRESH_TOKEN_IDLE_EXPIRES_SECONDS = getSeconds("openid.refreshTokenIdleExpires",
            DEFAULT_REFRESH_TOKEN_IDLE_EXPIRES_SECONDS);
    private static final long REFRESH_TOKEN_MAX_EXPIRES_SECONDS = getSeconds("openid.refreshTokenMaxExpires",
            DEFAULT_REFRESH_TOKEN_MAX_EXPIRES_SECONDS);
    private static final long ACCESS_TOKEN_EXPIRES_MILLIS = TimeUnit.SECONDS.toMillis(ACCESS_TOKEN_EXPIRES_SECONDS);
    private static final long REFRESH_TOKEN_IDLE_EXPIRES_MILLIS = TimeUnit.SECONDS.toMillis(
            REFRESH_TOKEN_IDLE_EXPIRES_SECONDS);
    private static final long REFRESH_TOKEN_MAX_EXPIRES_MILLIS = TimeUnit.SECONDS.toMillis(
            REFRESH_TOKEN_MAX_EXPIRES_SECONDS);

    @Inject
    private OpenIdRefreshTokenRepository openIdRefreshTokenRepository;

    @Inject
    private UserQueryService userQueryService;

    public JSONObject issueToken(final String userId, final Collection<String> scopes, final String realm)
            throws ServiceException {
        final Transaction transaction = openIdRefreshTokenRepository.beginTransaction();
        try {
            final JSONObject data = issueTokenInternal(userId, String.join(" ", scopes), realm, null, null);
            transaction.commit();
            return data;
        } catch (final Exception e) {
            rollback(transaction);
            LOGGER.log(Level.ERROR, "Issues OpenID token failed", e);
            throw new ServiceException(e);
        }
    }

    public synchronized JSONObject refresh(final String refreshToken) throws ServiceException {
        if (!OpenIdUtil.isRefreshTokenFormat(refreshToken)) {
            throw new ServiceException("Unauthorized");
        }

        final Transaction transaction = openIdRefreshTokenRepository.beginTransaction();
        try {
            final String tokenHash = OpenIdUtil.hashRefreshToken(refreshToken);
            final JSONObject current = openIdRefreshTokenRepository.getByTokenHash(tokenHash);
            if (null == current) {
                throw new ServiceException("Unauthorized");
            }

            final long now = System.currentTimeMillis();
            if (OpenIdRefreshToken.STATE_ROTATED == current.optInt(OpenIdRefreshToken.STATE)) {
                revokeActiveFamily(current.optString(OpenIdRefreshToken.FAMILY_ID), now);
                transaction.commit();
                throw new ServiceException("Unauthorized");
            }

            if (OpenIdRefreshToken.STATE_ACTIVE != current.optInt(OpenIdRefreshToken.STATE)) {
                throw new ServiceException("Unauthorized");
            }

            if (current.optLong(OpenIdRefreshToken.IDLE_EXPIRES_AT) <= now
                    || current.optLong(OpenIdRefreshToken.MAX_EXPIRES_AT) <= now) {
                current.put(OpenIdRefreshToken.STATE, OpenIdRefreshToken.STATE_EXPIRED);
                current.put(OpenIdRefreshToken.UPDATED_AT, now);
                openIdRefreshTokenRepository.update(current.optString(Keys.OBJECT_ID), current);
                transaction.commit();
                throw new ServiceException("Unauthorized");
            }

            final JSONObject user = userQueryService.getUser(current.optString(OpenIdRefreshToken.USER_ID));
            if (null == user || UserExt.USER_STATUS_C_VALID != user.optInt(UserExt.USER_STATUS)) {
                current.put(OpenIdRefreshToken.STATE, OpenIdRefreshToken.STATE_REVOKED);
                current.put(OpenIdRefreshToken.UPDATED_AT, now);
                openIdRefreshTokenRepository.update(current.optString(Keys.OBJECT_ID), current);
                transaction.commit();
                throw new ServiceException("Unauthorized");
            }

            current.put(OpenIdRefreshToken.STATE, OpenIdRefreshToken.STATE_ROTATED);
            current.put(OpenIdRefreshToken.LAST_USED_AT, now);
            current.put(OpenIdRefreshToken.UPDATED_AT, now);
            openIdRefreshTokenRepository.update(current.optString(Keys.OBJECT_ID), current);

            final JSONObject data = issueTokenInternal(current.optString(OpenIdRefreshToken.USER_ID),
                    current.optString(OpenIdRefreshToken.SCOPE), current.optString(OpenIdRefreshToken.REALM),
                    current.optString(OpenIdRefreshToken.FAMILY_ID), current);
            transaction.commit();
            return data;
        } catch (final ServiceException e) {
            rollback(transaction);
            throw e;
        } catch (final Exception e) {
            rollback(transaction);
            LOGGER.log(Level.ERROR, "Refreshes OpenID token failed", e);
            throw new ServiceException(e);
        }
    }

    private JSONObject issueTokenInternal(final String userId, final String scope, final String realm,
                                          final String familyId, final JSONObject parent)
            throws Exception {
        final long now = System.currentTimeMillis();
        final String normalizedScope = StringUtils.trimToEmpty(scope);
        final String normalizedRealm = StringUtils.left(StringUtils.trimToEmpty(realm), 512);
        final long maxExpiresAt = null == parent
                ? now + REFRESH_TOKEN_MAX_EXPIRES_MILLIS
                : parent.optLong(OpenIdRefreshToken.MAX_EXPIRES_AT);
        final long idleExpiresAt = Math.min(now + REFRESH_TOKEN_IDLE_EXPIRES_MILLIS, maxExpiresAt);
        if (idleExpiresAt <= now) {
            throw new ServiceException("Unauthorized");
        }

        final String token = OpenIdUtil.generateRefreshToken();
        final String tokenId = Ids.genTimeMillisId();
        final JSONObject refreshToken = new JSONObject()
                .put(Keys.OBJECT_ID, tokenId)
                .put(OpenIdRefreshToken.TOKEN_HASH, OpenIdUtil.hashRefreshToken(token))
                .put(OpenIdRefreshToken.USER_ID, userId)
                .put(OpenIdRefreshToken.REALM, normalizedRealm)
                .put(OpenIdRefreshToken.SCOPE, normalizedScope)
                .put(OpenIdRefreshToken.FAMILY_ID, StringUtils.isBlank(familyId)
                        ? UUID.randomUUID().toString().replace("-", "")
                        : familyId)
                .put(OpenIdRefreshToken.PARENT_ID, null == parent ? "" : parent.optString(Keys.OBJECT_ID))
                .put(OpenIdRefreshToken.STATE, OpenIdRefreshToken.STATE_ACTIVE)
                .put(OpenIdRefreshToken.CREATED_AT, now)
                .put(OpenIdRefreshToken.UPDATED_AT, now)
                .put(OpenIdRefreshToken.LAST_USED_AT, 0L)
                .put(OpenIdRefreshToken.IDLE_EXPIRES_AT, idleExpiresAt)
                .put(OpenIdRefreshToken.MAX_EXPIRES_AT, maxExpiresAt);
        openIdRefreshTokenRepository.add(refreshToken);

        final long accessExpiresAt = now + ACCESS_TOKEN_EXPIRES_MILLIS;
        return new JSONObject()
                .put(SCOPE, normalizedScope)
                .put(TOKEN_TYPE_KEY, TOKEN_TYPE)
                .put(ACCESS_TOKEN, OpenIdUtil.generateAccessToken(userId,
                        StringUtils.isBlank(normalizedScope) ? java.util.Collections.emptyList() : java.util.Arrays.asList(normalizedScope.split("\\s+")),
                        normalizedRealm, accessExpiresAt))
                .put(EXPIRES_IN, ACCESS_TOKEN_EXPIRES_SECONDS)
                .put(REFRESH_TOKEN, token)
                .put(REFRESH_EXPIRES_IN, Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(idleExpiresAt - now)));
    }

    private void revokeActiveFamily(final String familyId, final long now) throws RepositoryException {
        final List<JSONObject> activeTokens = openIdRefreshTokenRepository.getActiveTokensByFamilyId(familyId);
        for (final JSONObject activeToken : activeTokens) {
            activeToken.put(OpenIdRefreshToken.STATE, OpenIdRefreshToken.STATE_REVOKED);
            activeToken.put(OpenIdRefreshToken.UPDATED_AT, now);
            openIdRefreshTokenRepository.update(activeToken.optString(Keys.OBJECT_ID), activeToken);
        }
    }

    private void rollback(final Transaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private static long getSeconds(final String key, final long defaultValue) {
        final String value = StringUtils.trimToEmpty(Symphonys.get(key));
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }

        try {
            final long ret = Long.parseLong(value);
            return ret > 0 ? ret : defaultValue;
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }
}
