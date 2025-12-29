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
package org.b3log.symphony.ai;

public class OpenAIProvider implements Provider {
	private String token;

	public String Authorize() {
		return "Bearer " + this.token;
	}

	sealed interface MessageType permits MessageType.System, MessageType.User, MessageType.Assistant {
		record System(String content) implements MessageType {
			@Override
			public String toString() {
				return "system";
			}
		};

		record User(String content) implements MessageType {
			@Override
			public String toString() {
				return "user";
			}
		};

		record Assistant() implements MessageType {
			@Override
			public String toString() {
				return "assistant";
			}
		};
	};

	sealed interface ContentType permits ContentType.Text, ContentType.Image {
		record Text(String text) implements ContentType {};
		record Image(String data) implements ContentType {};
	}

	sealed interface Content permits Content.Text, Content.Array {
		record Text(String text) implements Content {};
		record Array(ContentType[] content) implements Content {};
	}
}
