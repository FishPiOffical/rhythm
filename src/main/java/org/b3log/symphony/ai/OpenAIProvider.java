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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.List;

import org.b3log.symphony.util.JSONsWriter;
import org.json.JSONStringer;
import org.json.JSONWriter;


public class OpenAIProvider implements Provider {
	public sealed interface Message extends JSONsWriter
			permits Message.System,
			Message.User,
			Message.Developer {

		public static List<Message> of(Message... messages) {
			return List.of(messages);
		}

		final record System(String content) implements Message {
			public JSONWriter write(JSONWriter write) {
				return write.object()
						.key("role").value("system")
						.key("content").value(content)
						.endObject();
			}
		};

		final record User(Content content) implements Message {
			private JSONWriter getContentType(JSONWriter write, List<ContentType> types) {
				write.array();
				for (ContentType type : types) {
					write = write.object();
					write = switch (type) {
						case ContentType.Text(String text) -> write
								.key("type").value("text")
								.key("text").value(text);
						case ContentType.Image(String data, String _) -> write
								.key("type").value("image_url")
								.key("image_url").object()
								.key("url").value(data)
								.endObject();
					};
					write = write.endObject();
				}
				return write.endArray();
			}

			public JSONWriter write(JSONWriter write) {
				write = write.object()
						.key("role").value("user")
						.key("content");
				write = switch (content) {
					case Provider.Content.Text(String text) -> write.value(text);
					case Provider.Content.Array(List<ContentType> types) -> this.getContentType(write, types);
				};

				return write.endObject();
			}
		};

		final record Developer(String content) implements Message {
			public JSONWriter write(JSONWriter write) {
				return write.object()
						.key("role").value("system")
						.key("content").value(content)
						.endObject();
			}
		};
	}

	public sealed interface Options extends JSONsWriter
			permits Options.Stream,
			Options.MaxInputTokens,
			Options.MaxTokens,
			Options.Thinking,
			Options.EnableSearch,
			Options.StructureResponse {

		public sealed interface StructureResponse extends Options
				permits StructureResponse.Text,
				StructureResponse.Json {

			final record Text() implements StructureResponse {
				public JSONWriter write(JSONWriter write) {
					return write.key("response_format").object()
							.key("type").value("text")
							.endObject();
				}
			};

			final record Json() implements StructureResponse {
				public JSONWriter write(JSONWriter write) {
					return write.key("response_format").object()
							.key("type").value("json_object")
							.endObject();
				}
			};
		}

		final record Stream(boolean b, boolean includeUsage) implements Options {
			public JSONWriter write(JSONWriter write) {
				write = write.key("stream").value(b);
				if (b) {
					write = write.key("stream_options").object()
						.key("include_usage").value(b)
						.endObject();
				}

				return write;
			}
		};

		final record Thinking(boolean b) implements Options {
			public JSONWriter write(JSONWriter write) {
				return write.key("thinking").value(b);
			}
		}

		final record MaxInputTokens(Integer n) implements Options {
			public JSONWriter write(JSONWriter write) {
				return write.key("max_input_tokens").value(n);
			}
		}

		final record MaxTokens(Integer n) implements Options {
			public JSONWriter write(JSONWriter write) {
				return write.key("max_tokens").value(n);
			}
		}

		final record EnableSearch(boolean enable) implements Options {
			public JSONWriter write(JSONWriter write) {
				return write.key("enable_search").value(enable);
			}
		}
	}

	private URI uri;
	private Model model;
	private Authorize token;

	private List<Options> options;
	private List<Message> messages;

	public OpenAIProvider(URI uri, Model model, Authorize token, List<Message> messages, Options... options)
			throws ModelNotSupportException {
		this.verifyModelSupported(model, messages);

		this.uri = uri;
		this.token = token;
		this.model = model;
		this.messages = messages;
		this.options = List.of(options);
	}

	public HttpRequest getHttpRequest() {
		var obj = new JSONStringer().object()
				.key("model").value(this.model.getName());

		obj = obj.key("messages").array();
		for (Message message : this.messages) {
			obj = message.write(obj);
		}
		obj = obj.endArray();

		for (Options opt : this.options) {
			obj = opt.write(obj);
		}
		var body = obj.endObject().toString();
		var request = HttpRequest.newBuilder()
			.uri(this.uri)
			.header("Content-Type", "application/json")
			.headers(this.Authorization())
			.POST(BodyPublishers.ofString(body));

		return request.build();
	}

	private String[] Authorization() {
		return switch (this.token) {
			case Provider.Authorize.Token(String token) -> new String[] { "Authorization", "Bearer " + token };
		};
	}

	private void verifyModelSupported(Model model, List<Message> messages) throws ModelNotSupportException {
		var supportedText = model instanceof Model.Supported.Text;
		var supportedImage = model instanceof Model.Supported.Image;

		for (Message message : messages) {
			switch (message) {
				case Message.User(Content content) when supportedText -> {
					if (content instanceof Content.Array array) {
						for (ContentType t : array.content()) {
							if (!supportedImage && t instanceof ContentType.Image _) {
								throw new ModelNotSupportException(model, "image");
							}
						}
					}
				}
				default -> {
					if (!supportedText) {
						throw new ModelNotSupportException(model, "text");
					}
				}
			}
		}
	}
}
