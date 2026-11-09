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
package org.b3log.symphony.processor;

import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3SettingsMgmtService;
import org.b3log.symphony.service.ApiV3AvatarQueryService;
import org.b3log.symphony.service.ApiV3PersonalityQueryService;
import org.b3log.symphony.util.ApiV3Personality;
import org.b3log.symphony.util.ApiV3Settings;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;

@Singleton
public class ApiV3SettingsProcessor {
    @Inject
    private ApiV3SettingsMgmtService management;
    @Inject
    private ApiV3AvatarQueryService avatars;
    @Inject
    private ApiV3PersonalityQueryService personalities;
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3SettingsProcessor p = beans.getReference(ApiV3SettingsProcessor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);
        Dispatcher.get("/api/v3/me/settings/personality", p::personality, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/personality-options", p::personalityOptions, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/avatar", p::avatar, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/profile", p::profile, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/privacy", p::privacy, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/display", p::display, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/skins", p::skins, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/functions", p::functions, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/location", p::location, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/locale", p::locale, auth::profileRead);
        Dispatcher.get("/api/v3/me/settings/locale-options", p::localeOptions, auth::profileRead);
        Dispatcher.put("/api/v3/me/settings/personality", p::updatePersonality, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/avatar", p::updateAvatar, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/functions", p::updateFunctions, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/location", p::updateLocation, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/locale", p::updateLocale, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/profile", p::updateProfile, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/privacy", p::updatePrivacy, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/me/settings/display", p::updateDisplay, auth::settingsWrite, writes::handle);
    }
    public void personality(final RequestContext c) {
        ApiV3Requests.read(c, () -> personalities.read(actor(c)));
    }

    public void personalityOptions(final RequestContext c) {
        ApiV3Requests.read(c, ApiV3Personality::options);
    }

    public void avatar(final RequestContext c) {
        ApiV3Requests.read(c, () -> avatars.read(actor(c)));
    }

    public void profile(final RequestContext c) {
        read(c, "profile");
    }

    public void privacy(final RequestContext c) {
        read(c, "privacy");
    }

    public void display(final RequestContext c) {
        read(c, "display");
    }

    public void skins(final RequestContext c) {
        ApiV3Requests.read(c, management::skins);
    }

    public void updatePersonality(final RequestContext c) {
        update(c, "personality");
    }

    public void updateAvatar(final RequestContext c) {
        update(c, "avatar");
    }

    public void updateProfile(final RequestContext c) {
        update(c, "profile");
    }

    public void updatePrivacy(final RequestContext c) {
        update(c, "privacy");
    }

    public void updateDisplay(final RequestContext c) {
        update(c, "display");
    }

    public void functions(final RequestContext c) {
        read(c, "functions");
    }

    public void location(final RequestContext c) {
        read(c, "location");
    }

    public void locale(final RequestContext c) {
        read(c, "locale");
    }

    public void localeOptions(final RequestContext c) {
        ApiV3Requests.read(c, management::localeOptions);
    }

    public void updateFunctions(final RequestContext c) {
        update(c, "functions");
    }

    public void updateLocation(final RequestContext c) {
        update(c, "location");
    }

    public void updateLocale(final RequestContext c) {
        update(c, "locale");
    }

    private void read(final RequestContext c, final String group) {
        ApiV3Requests.read(c, () -> management.read(actor(c), group));
    }
    private void update(final RequestContext c, final String group) {
        ApiV3Requests.respond(c, () -> management.update(actor(c), group, ApiV3Settings.normalize(group,
                ApiV3WriteInputs.body(c, ApiV3Settings.fields(group).keySet(), false), Latkes.getServePath()), ApiV3WriteInputs.idempotencyKey(c)));
    }
    private static String actor(final RequestContext c) {
        return ApiV3Requests.viewer(c).optString(Keys.OBJECT_ID);
    }
}
