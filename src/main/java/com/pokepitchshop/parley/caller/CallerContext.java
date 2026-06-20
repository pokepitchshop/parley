package com.pokepitchshop.parley.caller;

import org.springframework.util.StringUtils;

public record CallerContext(String displayName, String lastSummary) {

	public static final String DEFAULT_OPENING = """
			Hi, you're through to Poke Pitch Shop. What can I help you with?
			""";

	public static CallerContext anonymous() {
		return new CallerContext(null, null);
	}

	public boolean isReturning() {
		return StringUtils.hasText(displayName) || StringUtils.hasText(lastSummary);
	}

	public String openingGreeting() {
		if (StringUtils.hasText(displayName)) {
			return "Hi " + displayName.trim() + ", welcome back to Poke Pitch Shop. What can I help you with?";
		}
		if (StringUtils.hasText(lastSummary)) {
			return "Welcome back to Poke Pitch Shop. Good to hear from you again. What can I help you with?";
		}
		return DEFAULT_OPENING.trim();
	}

	public String systemPromptSnippet() {
		if (!isReturning()) {
			return """
					This caller is not recognized yet. If they tell you their name, use it naturally. \
					You may ask once if knowing their name would help.
					""";
		}
		StringBuilder snippet = new StringBuilder("This is a returning caller.");
		if (StringUtils.hasText(displayName)) {
			snippet.append(" Their name is ").append(displayName.trim()).append(".");
		}
		if (StringUtils.hasText(lastSummary)) {
			snippet.append(" Summary of their last call: ").append(lastSummary.trim());
		}
		snippet.append(" Use this context naturally when relevant.");
		return snippet.toString();
	}

}
