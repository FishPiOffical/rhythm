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
package org.b3log.symphony.util;

import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;

/** 事件批次准备只影响读取，事件数量、顺序和失败重试游标保持独立。 */
public class ApiV3ChatStreamTest {
    static class Source implements ApiV3ChatStream.Source {
        long head;
        final List<JSONObject> rows = new ArrayList<>();
        public long head() { return head; }
        public List<JSONObject> after(long revision, long through, int limit) {
            return rows.stream().filter(row -> row.getLong("revision") > revision && row.getLong("revision") <= through).limit(limit).toList();
        }
        void add(long revision, String id) {
            rows.add(new JSONObject().put("revision", revision).put("messageId", id).put("kind", "updated")); head = revision;
        }
    }
    static class Sink implements ApiV3ChatStream.Sink {
        int prepares, finishes, syncs;
        boolean failPrepare, failSend;
        final List<Long> delivered = new ArrayList<>();
        public void prepare(List<JSONObject> rows) { prepares++; if (failPrepare) throw new IllegalStateException("test prepare"); }
        public void finished() { finishes++; }
        public void changed(String kind, String id, long revision) {
            if (failSend) throw new IllegalStateException("test send"); delivered.add(revision);
        }
        public void syncRequired() { syncs++; }
    }
    public static void main(String[] args) throws Exception {
        final var source = new Source(); final var sink = new Sink(); final var stream = new ApiV3ChatStream(source, sink);
        stream.poll(); Assert.assertEquals(sink.syncs, 1);
        source.add(1, "1"); source.add(2, "1"); source.add(3, "2"); stream.poll();
        Assert.assertEquals(sink.prepares, 1); Assert.assertEquals(sink.finishes, 1);
        Assert.assertEquals(sink.delivered, List.of(1L, 2L, 3L));
        stream.poll(); Assert.assertEquals(sink.prepares, 1);
        source.add(4, "2"); sink.failPrepare = true;
        try { stream.poll(); Assert.fail(); } catch (IllegalStateException expected) { }
        Assert.assertEquals(sink.finishes, 2); Assert.assertEquals(sink.delivered.size(), 3);
        sink.failPrepare = false; sink.failSend = true;
        try { stream.poll(); Assert.fail(); } catch (IllegalStateException expected) { }
        Assert.assertEquals(sink.finishes, 3);
        sink.failSend = false; stream.poll(); Assert.assertEquals(sink.delivered, List.of(1L, 2L, 3L, 4L));
        source.add(5, "1"); source.add(7, "2"); stream.poll();
        Assert.assertEquals(sink.syncs, 2); Assert.assertEquals(sink.delivered.size(), 4);
        source.head = 0; stream.poll(); Assert.assertEquals(sink.syncs, 3);
        source.rows.clear(); source.add(1, "1"); source.add(2, "invalid");
        final int prepared = sink.prepares;
        try { stream.poll(); Assert.fail(); } catch (IllegalStateException expected) { }
        Assert.assertEquals(sink.prepares, prepared);
        Assert.assertEquals(sink.delivered.size(), 4);
        System.out.println("PASS ordered duplicate events, one prepare per batch, prepare/send retry cleanup, gap/reset and full validation");
    }
}
