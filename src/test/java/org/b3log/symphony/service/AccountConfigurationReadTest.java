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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.apache.commons.codec.binary.Base32;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.http.Request;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.http.Response;
import org.b3log.latke.http.renderer.JsonRenderer;
import org.b3log.latke.ioc.Bean;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.SingletonContext;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.ApiProcessor;
import org.b3log.symphony.processor.ApiV3Processor;
import org.b3log.symphony.processor.LoginProcessor;
import org.b3log.symphony.processor.MFAProcessor;
import org.b3log.symphony.repository.AccountCredentialStateRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.AccountCredentialVersions;
import org.b3log.symphony.util.MultiFactorAuthenticator;
import org.b3log.symphony.util.StatusCodes;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 对现有查询与登录处理器执行配置读取、异常响应及兼容性回归。 */
public class AccountConfigurationReadTest {
    private static Object field(final Object target, final Class<?> owner, final String name) throws Exception {
        final var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void inject(final Object target, final Class<?> owner, final String name, final Object value)
            throws Exception {
        final var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture implements AutoCloseable {
        final String userId = "987654321";
        final String credential = UUID.randomUUID().toString().replace("-", "");
        final JSONObject user = new JSONObject().put(Keys.OBJECT_ID, userId)
                .put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID)
                .put(User.USER_PASSWORD, credential);
        final RepositoryException failure = new RepositoryException("test configuration read failure");
        String secret = "";
        boolean failRead;
        final Map<Bean<?>, Object> previousReferences = new HashMap<>();
        final List<Bean<?>> addedBeans = new ArrayList<>();
        final UserQueryService query = new UserQueryService() {
            @Override
            public JSONObject getUserByName(final String name) { return user; }
        };
        final LoginProcessor login = new LoginProcessor();
        final ApiProcessor legacy = new ApiProcessor();
        final ApiV3Processor v3 = new ApiV3Processor();
        final BeanManager manager = BeanManager.getInstance();
        final SingletonContext context = (SingletonContext) field(manager, BeanManager.class, "context");

        Fixture() throws Exception {
            inject(query, UserQueryService.class, "userRepository", new UserRepository() {
                @Override
                public JSONObject get(final Query query) throws RepositoryException {
                    if (failRead) throw failure;
                    return new JSONObject().put(Keys.RESULTS,
                            (Object) List.of(new JSONObject().put("secret2fa", secret)));
                }
            });
            bind(UserQueryService.class, query);
            bind(AccountCredentialStateRepository.class, new AccountCredentialStateRepository() {
                @Override
                public JSONObject current(final String actor) {
                    return new JSONObject().put(Keys.OBJECT_ID, userId).put("credentialEpoch", 0)
                            .put(UserExt.USER_STATUS, UserExt.USER_STATUS_C_VALID)
                            .put(User.USER_PASSWORD, credential);
                }
            });
            final var language = new LangPropsService() {
                @Override
                public String get(final String key) { return key; }
            };
            inject(login, LoginProcessor.class, "userQueryService", query);
            inject(login, LoginProcessor.class, "langPropsService", language);
            inject(legacy, ApiProcessor.class, "userQueryService", query);
            inject(legacy, ApiProcessor.class, "langPropsService", language);
            inject(v3, ApiV3Processor.class, "loginProcessor", login);
        }

        @SuppressWarnings("unchecked")
        private <T> void bind(final Class<T> type, final T instance) throws Exception {
            final Set<Bean<?>> beans = (Set<Bean<?>>) field(manager, BeanManager.class, "beans");
            final boolean existed = beans.stream().anyMatch(bean -> bean.getBeanClass().equals(type));
            final Bean<T> bean = manager.getConfigurator().createBean(type);
            final var references = (Map<Bean<?>, Object>) field(context, SingletonContext.class, "beanReferences");
            previousReferences.put(bean, references.get(bean));
            if (!existed) addedBeans.add(bean);
            context.add(bean, instance);
        }

        LoginProcessor.LoginResult authenticate(final String code) throws Exception {
            return login.authenticate("test-user", credential, code, "127.0.0.1");
        }

        JSONObject http(final String endpoint, final boolean modern) {
            final String body = new JSONObject().put("nameOrEmail", "test-user")
                    .put(User.USER_PASSWORD, credential).toString();
            final var raw = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, endpoint,
                    Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
            try {
                raw.headers().set("Content-Type", "application/json");
                raw.headers().set("X-Real-IP", "127.0.0.1");
                final var request = new Request(null, raw);
                request.setBytes(body.getBytes(StandardCharsets.UTF_8));
                final var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                final var call = new RequestContext(request, new Response(null, response));
                if (modern) v3.login(call);
                else if (endpoint.equals("/login")) login.login(call);
                else legacy.getKey(call);
                final JSONObject result = ((JsonRenderer) call.getRenderer()).getJSONObject();
                Assert.assertTrue(call.getResponse().getCookies().isEmpty());
                Assert.assertFalse(result.has(Keys.TOKEN));
                Assert.assertFalse(result.has("Key"));
                Assert.assertNull(AccountCredentialVersions.saved());
                if (modern) {
                    Assert.assertEquals(response.status().code(), 500);
                    Assert.assertEquals(result.getString("code"), "internal_error");
                    Assert.assertTrue(result.isNull("data"));
                } else {
                    Assert.assertEquals(result.getInt("code"), StatusCodes.ERR);
                }
                return result;
            } finally {
                raw.release();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void close() throws Exception {
            AccountCredentialVersions.clear();
            LoginProcessor.WRONG_PWD_TRIES.remove(userId);
            final var references = (Map<Bean<?>, Object>) field(context, SingletonContext.class, "beanReferences");
            previousReferences.forEach((bean, previous) -> {
                if (previous == null) references.remove(bean);
                else references.put(bean, previous);
            });
            ((Set<Bean<?>>) field(manager, BeanManager.class, "beans")).removeAll(addedBeans);
        }
    }

    @Test(timeOut = 60_000)
    public void readsAndLoginResponses() throws Exception {
        final var properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        Latkes.setLatkeProps(properties);
        Latkes.setLocalProps(properties);
        try (var fixture = new Fixture()) {
            Assert.assertEquals(fixture.query.getSecret2fa(fixture.userId), "");
            Assert.assertTrue(fixture.authenticate("").success());
            fixture.secret = MultiFactorAuthenticator.generateSecretKey();
            Assert.assertEquals(fixture.query.getSecret2fa(fixture.userId), fixture.secret);
            Assert.assertEquals(fixture.authenticate("").code(), "mfa_required");
            Assert.assertEquals(fixture.authenticate("1000000").code(), "invalid_mfa");
            final var method = MultiFactorAuthenticator.class.getDeclaredMethod("verifyCode", byte[].class, long.class);
            method.setAccessible(true);
            final String code = method.invoke(null, new Base32().decode(fixture.secret),
                    System.currentTimeMillis() / 30_000L).toString();
            Assert.assertTrue(fixture.authenticate(code).success());
            fixture.failRead = true;
            try {
                fixture.query.getSecret2fa(fixture.userId);
                Assert.fail("Expected configuration read error");
            } catch (final IllegalStateException error) {
                Assert.assertSame(error.getCause(), fixture.failure);
            }
            for (final String attempt : List.of("", code)) {
                try {
                    fixture.authenticate(attempt);
                    Assert.fail("Expected service error");
                } catch (final ServiceException error) {
                    Assert.assertSame(error.getCause().getCause(), fixture.failure);
                    Assert.assertNull(AccountCredentialVersions.saved());
                }
            }
            fixture.http("/api/v3/auth/login", true);
            fixture.http("/login", false);
            fixture.http("/api/getKey", false);
            fixture.failRead = false;
            fixture.secret = "";
            Assert.assertTrue(fixture.authenticate("").success());
        }
    }

    public static void main(final String[] args) throws Exception {
        new AccountConfigurationReadTest().readsAndLoginResponses();
        System.out.println("PASS account configuration reads, login error responses and compatibility");
    }
}
