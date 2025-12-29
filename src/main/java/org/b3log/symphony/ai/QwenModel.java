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

import java.util.Base64;

public sealed interface QwenModel permits QwenModel.Qwen3, QwenModel.Qwen {
	sealed abstract class Base implements Model {
		private Provider provider = new OpenAIProvider();

		abstract public String getName();

		@Override
		public Provider getProvider() {
			return this.provider;
		}
	}

	sealed interface Qwen extends QwenModel {
		final class VlOcr extends QwenModel.Base implements Qwen, Model.Image {
			@Override
			public String getName() {
				return "qwen3-vl-plus";
			}

			@Override
			public Model.Message.Image data(String url) {
				return new Model.Message.Image() {
					@Override
					public String url() {
						return String.format("{'url': '%s'}", url);
					};
				};
			}

			@Override
			public Model.Message.Image data(byte[] bin) {
				return new Model.Message.Image() {
					@Override
					public String url() {
						return String.format("{'url': '%s'}", Base64.getEncoder().encode(bin).toString());
					};
				};
			}
		}
	}

	sealed interface Qwen3 extends QwenModel {
		final class VlPlus extends QwenModel.Base implements Qwen3, Model.Text, Model.Image {
			@Override
			public String getName() {
				return "qwen3-vl-plus";
			}

			@Override
			public Model.Message.Text text(String text) {
				return new Model.Message.Text() {
					@Override
					public String text() {
						return text;
					}
				};
			}

			@Override
			public Model.Message.Image data(String url) {
				return new Model.Message.Image() {
					@Override
					public String url() {
						return String.format("{'url': '%s'}", url);
					};
				};
			}

			@Override
			public Model.Message.Image data(byte[] bin) {
				return new Model.Message.Image() {
					@Override
					public String url() {
						return String.format("{'url': '%s'}", Base64.getEncoder().encode(bin).toString());
					};
				};
			}
		}
	}
}
