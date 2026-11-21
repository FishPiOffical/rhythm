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
import org.b3log.latke.repository.Transaction;
import org.b3log.symphony.repository.AdminOperationTaskRepository;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AdminImageReviewServiceTest {
    static {
        final var configuration = new java.util.Properties();
        configuration.setProperty("runtimeDatabase", "NONE");
        Latkes.setLatkeProps(configuration);
        Latkes.setLocalProps(configuration);
    }

    private static final class Fixture extends AdminImageReviewService {
        int deletes;
        int refreshes;
        boolean failDelete;
        final JSONObject task;

        Fixture(final String status) throws Exception {
            task = new JSONObject().put("oId", "image-review:1").put("actorId", "2")
                    .put("status", status).put("kind", "IMAGE_REVIEW")
                    .put("payload", new JSONObject().put("key", "fixture").put("path", "https://example.invalid/fixture").toString());
            final AdminOperationTaskRepository repository = new AdminOperationTaskRepository() {
                @Override public JSONObject lock(final String id) { return new JSONObject(task.toString()); }
                @Override public void update(final String id, final JSONObject row, final String... fields) {
                    for (final String field : fields) task.put(field, row.get(field));
                }
                @Override public Transaction beginTransaction() {
                    return new Transaction() {
                        private boolean active = true;
                        @Override public String getId() { return "fixture"; }
                        @Override public boolean isActive() { return active; }
                        @Override public void commit() { active = false; }
                        @Override public void rollback() { active = false; }
                    };
                }
            };
            final var field = AdminImageReviewService.class.getDeclaredField("tasks");
            field.setAccessible(true);
            field.set(this, repository);
        }

        @Override protected void deleteCloudObject(final String key) throws Exception {
            deletes++;
            if (failDelete) throw new IllegalStateException("fixture delete timeout");
        }

        @Override protected void refreshCdn(final String path) throws Exception {
            refreshes++;
            throw new IllegalStateException("fixture CDN timeout");
        }

        JSONObject completeReview() throws Exception {
            return complete(new JSONObject().put("oId", "1").put("reviewed", 0), "2");
        }
    }

    @Test public void unknownDeleteDoesNotRunCdnOrRetry() throws Exception {
        final Fixture f = new Fixture("IMAGE_QUEUED");
        f.failDelete = true;
        Assert.assertEquals(f.completeReview().getString("status"), "UNKNOWN");
        Assert.assertEquals(f.completeReview().getString("status"), "UNKNOWN");
        Assert.assertEquals(f.deletes, 1);
        Assert.assertEquals(f.refreshes, 0);
        Assert.assertEquals(new JSONObject(f.task.getString("result")).getString("uncertainPhase"), "IMAGE_DELETING");
    }

    @Test public void confirmedDeleteIsNeverRepeatedAfterCdnFailure() throws Exception {
        final Fixture f = new Fixture("IMAGE_DELETED");
        Assert.assertEquals(f.completeReview().getString("status"), "UNKNOWN");
        Assert.assertEquals(f.completeReview().getString("status"), "UNKNOWN");
        Assert.assertEquals(f.deletes, 0);
        Assert.assertEquals(f.refreshes, 1);
        Assert.assertEquals(new JSONObject(f.task.getString("result")).getString("uncertainPhase"), "IMAGE_REFRESHING");
    }

    @Test public void finishedReviewOnlyReturnsResult() throws Exception {
        final Fixture f = new Fixture("IMAGE_DONE");
        Assert.assertEquals(f.completeReview().getInt("reviewed"), 1);
        Assert.assertEquals(f.deletes, 0);
        Assert.assertEquals(f.refreshes, 0);
    }

    public static void main(final String[] args) throws Exception {
        final var test = new AdminImageReviewServiceTest();
        test.unknownDeleteDoesNotRunCdnOrRetry();
        test.confirmedDeleteIsNeverRepeatedAfterCdnFailure();
        test.finishedReviewOnlyReturnsResult();
        System.out.println("PASS: image review cloud checkpoints and safe replay");
    }
}
