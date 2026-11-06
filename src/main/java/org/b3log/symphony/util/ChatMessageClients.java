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

import java.util.List;

/** Existing chat client-label contract; unknown brands are omitted. */
public final class ChatMessageClients {
    private ChatMessageClients() { }
    private static final List<String> LEGAL_CLIENT = List.of(
            "Web",
            "PC",
            "Mobile",
            "CLI",
            "uTools",
            "Windows",
            "macOS",
            "Linux",
            "IceNet",
            "ElvesOnline",
            "iOS",
            "Android",
            "Extension",
            "IDEA",
            "Chrome",
            "Edge",
            "VSCode",
            "Python",
            "Golang",
            "Bird",
            "Dart",
            "Harmony",
            "Rust",
            "Other");
    public static String normalize(final String input){
        try{
            final String[] parts=input.split("/");
            final String client=parts[0];
            String version=parts[1].replaceAll("[^0-9a-zA-Z\\u4e00-\\u9fa5.\\s\\-]","");
            if(version.length()>32)version=version.substring(0,31);
            return LEGAL_CLIENT.contains(client)?client+"/"+version:"";
        }catch(RuntimeException ignored){return "";}
    }
}
