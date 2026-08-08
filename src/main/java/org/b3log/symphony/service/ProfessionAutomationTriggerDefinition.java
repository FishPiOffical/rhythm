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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 自动化触发器字段定义。 */
record ProfessionAutomationTriggerDefinition(int schemaVersion, Map<String, ProfessionAutomationFieldType> fields) {

    static ProfessionAutomationTriggerDefinition create(final int version, final Object... fields) {
        final Map<String, ProfessionAutomationFieldType> result = new HashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            result.put((String) fields[index], (ProfessionAutomationFieldType) fields[index + 1]);
        }
        return new ProfessionAutomationTriggerDefinition(version, Map.copyOf(result));
    }
}

enum ProfessionAutomationFieldType {
    NUMBER(Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "IN", "IS_EMPTY")),
    TEXT(Set.of("EQ", "NE", "IN", "PREFIX", "SUFFIX", "CONTAINS", "IS_EMPTY")),
    TIME(Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "IN_WINDOW", "OLDER_THAN", "NEWER_THAN", "IS_EMPTY"));

    private final Set<String> operators;

    ProfessionAutomationFieldType(final Set<String> operators) {
        this.operators = operators;
    }

    Set<String> operators() {
        return operators;
    }
}
