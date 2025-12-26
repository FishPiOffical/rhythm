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

import java.util.*;

public class Tests {
    public static List<JSONObject> TAGS = Collections.synchronizedList(new ArrayList<JSONObject>() {
        @Override
        public boolean add(JSONObject o) {
            if (size() == 5) {
                remove(0);
            }
            return super.add(o);
        }
    });

    public static void main(String[] args) throws InterruptedException {
        //MD_CACHE = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) {
            int finalI = i;
            Thread.startVirtualThread(() -> TAGS.add(new JSONObject().put("hi", finalI + "")));
        }
        Thread.sleep(2000);
        System.out.println(TAGS.size());
        for (JSONObject object : TAGS) {
            System.out.println(object);
        }
    }
}
