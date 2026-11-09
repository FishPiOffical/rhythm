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

import org.b3log.symphony.model.ApiV3ChatEvent;
import org.json.JSONObject;
import java.util.List;

/** 按后端实例跟踪已提交事件的游标，遇到断档时要求客户端通过 HTTP 同步。 */
public final class ApiV3ChatStream {
    public interface Source {
        long head() throws Exception;
        List<JSONObject> after(long revision, long through, int limit) throws Exception;
    }

    public interface Sink {
        void changed(String kind, String messageId, long revision);
        void syncRequired();
    }

    private final Source source;
    private final Sink sink;
    private Long cursor;

    public ApiV3ChatStream(Source source, Sink sink) {
        this.source = source;
        this.sink = sink;
    }

    public synchronized void reset() {
        cursor = null;
    }

    public synchronized void poll() throws Exception {
        final long head = source.head();
        if (head < 0)
            throw new IllegalStateException("invalid chat head");

        // 首次轮询或房间头回退时，先探测读取边界，再要求客户端重新同步。
        if (cursor == null || head < cursor) {
            probe(head);
            sink.syncRequired();
            cursor = head;
            return;
        }
        if (head == cursor) {
            probe(head);
            return;
        }

        for (int batch = 0; cursor < head && batch < 5; batch++) {
            final List<JSONObject> rows = source.after(cursor, head, 200);
            if (rows.isEmpty()) {
                sink.syncRequired();
                cursor = head;
                return;
            }
            if (rows.size() > 200)
                throw new IllegalStateException("invalid chat event batch");
            for (final JSONObject row : rows) {
                final long revision = row.getLong("revision");
                final String id = row.getString("messageId"), kind = row.getString("kind");
                if (revision <= cursor || revision > head || !ApiV3ChatEvent.validChange(kind, id))
                    throw new IllegalStateException("invalid chat event");
                if (revision != cursor + 1) {
                    sink.syncRequired();
                    cursor = head;
                    return;
                }
                sink.changed(kind, id, revision);
                cursor = revision;
            }
        }
    }
    private void probe(long head) throws Exception {
        if (!source.after(head, head, 200).isEmpty())
            throw new IllegalStateException("invalid chat event probe");
    }
}
