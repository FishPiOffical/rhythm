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

public sealed interface QwenModel permits QwenModel.Qwen3, QwenModel.Qwen {
	sealed interface Qwen extends QwenModel {
		final class VlOcr implements Qwen, Model.Supported.Image {
			@Override
			public String getName() {
				return "qwen3-vl-plus";
			}
		}
	}

	sealed interface Qwen3 extends QwenModel {
		final class VlPlus implements Qwen3, Model.Supported.Text, Model.Supported.Image {
			@Override
			public String getName() {
				return "qwen3-vl-plus";
			}
		}
	}
}
