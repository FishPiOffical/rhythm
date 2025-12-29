package org.b3log.symphony.ai;

public class AIRequest {
	record Message(Message.Type role) {
		enum Type {
			System("system"),
			User("user");

			private String value;
			Type(String value) {
				this.value = value;
			}

			@Override
			public String toString() {
				return this.value;
			}
		};
	};
}
