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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.json.JSONObject;
import org.json.JSONException;

public class AIClient {
	private HttpClient client;

	public AIClient(HttpClient client) {
		this.client = client;
	}

	private class SSE implements Supplier<JSONObject> {
		private BufferedReader reader;
		private boolean closed = false;
		private static String DONE = "data: [DONE]";

		public SSE(InputStream inputStream) {
			this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
		}

		@Override
		public JSONObject get() {
				try {
					String line;
					do {
						line = reader.readLine();
						if (line == null || line.equals(SSE.DONE)) {
							this.reader.close();
							this.closed = true;

							return null;
						}
					} while (line.isBlank());


					return new JSONObject(line.substring(6));
				} catch (IOException | JSONException e) {
					if (!this.closed) {
						try {
							this.reader.close();
						} catch (IOException ignore) {}
					}
					this.closed = true;

					return null;
				}
		}
	}

	private class Text implements Supplier<JSONObject> {
		private BufferedReader reader;
		private boolean closed = false;

		public Text(InputStream inputStream) {
			this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
		}

		@Override
		public JSONObject get() {
			try {
				var line = reader.readAllAsString();
				this.reader.close();
				this.closed = true;
				if (line == null) {
					return null;
				}

				return new JSONObject(line);
			} catch (IOException | JSONException e) {
				if (!this.closed) {
					try {
						this.reader.close();
					} catch (IOException ignore) {}
				}
				this.closed = true;

				return null;
			}
		}
	}

	public Stream<JSONObject> send(Provider provider) throws IOException, InterruptedException, JSONException {
		var response = this.client.send(provider.getHttpRequest(), HttpResponse.BodyHandlers.ofInputStream());
		var contentType = response.headers().firstValue("Content-Type").orElse("");
		var handler = contentType.contains("event-stream")
			? new SSE(response.body())
			: new Text(response.body());
		return Stream.generate(handler).takeWhile(Objects::nonNull);
	}
}
