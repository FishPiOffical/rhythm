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

import org.b3log.latke.Latkes;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.ProfessionContributionIncrement;
import org.b3log.symphony.repository.ProfessionEventEffectRepository;
import org.b3log.symphony.repository.UserProfessionContributionRepository;
import org.b3log.symphony.repository.UserProfessionRepository;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ProfessionEffectHardeningTest {
    private Object previousLatkeProps;
    private Object previousLocalProps;
    private static void inject(final Object target, final String name, final Object value) throws Exception {
        final var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeClass
    public void initializeLatke() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty("runtimeDatabase", "NONE");
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            if ("latkeProps".equals(name)) previousLatkeProps = field.get(null);
            else previousLocalProps = field.get(null);
            field.set(null, properties);
        }
    }

    @AfterClass(alwaysRun = true)
    public void restoreLatke() throws Exception {
        for (final String name : List.of("latkeProps", "localProps")) {
            final var field = Latkes.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, "latkeProps".equals(name) ? previousLatkeProps : previousLocalProps);
        }
    }

    @Test(timeOut = 60000)
    public void notificationFailureDoesNotRetryCommittedEffect() throws Exception {
        for (final String scenario : List.of("notification", "classification", "other", "business")) {
            final Fixture fixture = new Fixture(scenario);
            fixture.run();
            if ("business".equals(scenario)) {
                Assert.assertEquals(fixture.retries, 1);
                Assert.assertFalse(fixture.committed);
                Assert.assertEquals(fixture.sends, 0);
            } else {
                Assert.assertEquals(fixture.retries, 0);
                Assert.assertTrue(fixture.committed);
                Assert.assertEquals(fixture.effect.getString("status"), "APPLIED");
                Assert.assertEquals(fixture.contributions, "other".equals(scenario) ? 1 : 0);
                Assert.assertEquals(fixture.sends, "notification".equals(scenario) ? 1 : 0);
            }
        }
    }

    @Test(timeOut = 60000)
    public void repositoryUsesExplicitColumnsAndParameterizedIndexKeys() throws Exception {
        final List<String> statements = new ArrayList<>();
        final List<Object> parameters = new ArrayList<>();
        final ProfessionEventEffectRepository repository = new ProfessionEventEffectRepository() {
            @Override public String getName() { return "symphony_profession_event_effect"; }
            @Override public List<JSONObject> select(final String sql, final Object... args) {
                statements.add(sql); parameters.add(args[0]); return List.of();
            }
        };
        repository.getNonReversalByEventKey("event");
        repository.getNonReversalBySourceEventId("source");
        Assert.assertEquals(parameters, List.of("event", "source"));
        Assert.assertTrue(statements.get(0).endsWith("WHERE `eventKey`=? AND `reversesEffectId`=''"));
        Assert.assertTrue(statements.get(1).endsWith("WHERE `sourceEventId`=? AND `reversesEffectId`=''"));
        for (final String sql : statements) {
            Assert.assertFalse(sql.contains("SELECT *"));
            for (final String column : List.of("oId", "eventKey", "sourceEventId", "bucketId", "bucketKey",
                    "automationRevisionId", "actionCode", "userId", "professionId", "professionRevisionId",
                    "levelSchemeId", "actualExperienceDelta", "beforeExperience", "afterExperience", "status", "createdAt")) {
                Assert.assertTrue(sql.contains("`" + column + "`"), column);
            }
        }
    }

    @Test(timeOut = 60000)
    public void sourceLookupPreservesOriginalEventFilteringAndOrder() throws Exception {
        final List<String> calls = new ArrayList<>();
        final var candidates = List.of(row("3", "event", "source", 20, 2),
                row("2", "event", "source", 10, 1), row("1", "other", "source", 1, 9),
                row("4", "event", "source", 2, 0));
        final ProfessionEventEffectRepository repository = new ProfessionEventEffectRepository() {
            @Override public List<JSONObject> getNonReversalBySourceEventId(final String id) {
                calls.add("source:" + id); return candidates;
            }
            @Override public List<JSONObject> getNonReversalByEventKey(final String key) {
                calls.add("event:" + key); return List.of(row("5", "event", "", 4, 1));
            }
        };
        final var service = new ProfessionAutomationNotificationService();
        inject(service, "effectRepository", repository);
        final Method method = ProfessionAutomationNotificationService.class.getDeclaredMethod("relatedExperienceEffects", JSONObject.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked") final List<JSONObject> result = (List<JSONObject>) method.invoke(service, row("9", "event", "source", 0, 0));
        Assert.assertEquals(result.stream().map(item -> item.getString("oId")).toList(), List.of("2", "3"));
        @SuppressWarnings("unchecked") final List<JSONObject> fallback = (List<JSONObject>) method.invoke(service, row("9", "event", "", 0, 0));
        Assert.assertEquals(fallback.getFirst().getString("oId"), "5");
        Assert.assertEquals(calls, List.of("source:source", "event:event"));
    }

    private static JSONObject row(final String id, final String event, final String source, final long created, final long delta) {
        return new JSONObject().put("oId", id).put("eventKey", event).put("sourceEventId", source)
                .put("automationRevisionId", "revision").put("userId", "user")
                .put("createdAt", created).put("actualExperienceDelta", delta);
    }

    private static final class Fixture {
        final String scenario;
        final JSONObject effect = row("1", "event", "source", 1, 10).put("professionId", "profession")
                .put("professionRevisionId", "revision").put("levelSchemeId", "scheme")
                .put("actionCode", "action").put("occurredAt", 1L).put("reversesEffectId", "");
        final JSONObject summary = new JSONObject().put("oId", "2").put("levelSchemeId", "scheme")
                .put("professionRevisionId", "revision").put("totalExperience", 0L)
                .put("currentLevelCode", "L1").put("highestReachedExperience", 0L).put("dataVersion", 0L);
        final ProfessionEffectApplyService service = new ProfessionEffectApplyService();
        boolean committed;
        int retries, sends, contributions;

        Fixture(final String scenario) throws Exception {
            this.scenario = scenario;
            inject(service, "effectRepository", new ProfessionEventEffectRepository() {
                @Override public JSONObject get(final String id) { return effect; }
                @Override public Transaction beginTransaction() {
                    return new Transaction() {
                        boolean active = true;
                        @Override public void commit() { committed = true; active = false; }
                        @Override public void rollback() { active = false; }
                        @Override public boolean isActive() { return active; }
                    };
                }
                @Override public void update(final String id, final JSONObject value, final String... fields) { }
            });
            inject(service, "effectExecutionService", new ProfessionEffectExecutionService() {
                @Override public Claim claim(final String id) { return new Claim(id, 1); }
                @Override public JSONObject lockClaimed(final Claim claim) { return effect; }
                @Override public void recordFailure(final Claim claim, final Exception error) { retries++; }
            });
            inject(service, "notificationService", new ProfessionAutomationNotificationService() {
                @Override public boolean isNotificationEffect(final JSONObject value) throws RepositoryException {
                    if ("classification".equals(scenario)) throw new RepositoryException("classification failure");
                    return "notification".equals(scenario);
                }
                @Override public void send(final JSONObject value) {
                    Assert.assertTrue(committed, "notification must run after commit");
                    sends++; throw new AssertionError("notification failure");
                }
            });
            inject(service, "userProfessionRepository", new UserProfessionRepository() {
                @Override public JSONObject getForUpdate(final String userId, final String professionId) { return summary; }
                @Override public void update(final String id, final JSONObject value, final String... fields) { }
            });
            inject(service, "levelResolver", new ProfessionLevelResolver() {
                @Override public JSONObject resolve(final String schemeId, final long experience) { return new JSONObject().put("levelCode", "L1"); }
                @Override public List<JSONObject> crossedLevels(final String schemeId, final long before, final long after) { return List.of(); }
            });
            inject(service, "rewardGrantService", new ProfessionRewardGrantService() {
                @Override public void grantCrossed(final JSONObject value, final List<JSONObject> levels, final long now) {
                    if ("business".equals(scenario)) throw new IllegalStateException("business failure");
                }
            });
            inject(service, "contributionRepository", new UserProfessionContributionRepository() {
                @Override public void increment(final ProfessionContributionIncrement increment) { contributions++; }
            });
            inject(service, "privacyMgmtService", new ProfessionPrivacyMgmtService() {
                @Override public void touchProgressInCurrentTransaction(final String userId, final long now) { }
            });
        }

        void run() throws Exception {
            final Method method = ProfessionEffectApplyService.class.getDeclaredMethod("apply", String.class);
            method.setAccessible(true);
            method.invoke(service, "1");
        }
    }
}
