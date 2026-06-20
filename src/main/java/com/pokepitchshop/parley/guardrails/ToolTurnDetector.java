package com.pokepitchshop.parley.guardrails;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ToolTurnDetector {

	private static final List<Pattern> TOOL_ACTION_PATTERNS = List.of(
			Pattern.compile("\\b(book|schedule|reserve|appointment)\\b"),
			Pattern.compile("\\b(cancel|refund|return) (my )?(order|booking|reservation)\\b"),
			Pattern.compile("\\b(look up|check|find) (my )?(order|account|reservation)\\b"),
			Pattern.compile("\\b(charge|pay|bill) (my )?(card|account)\\b"),
			Pattern.compile("\\b(send (me )?(an )?email|text me (a )?link)\\b"));

	public boolean looksLikeToolAction(String speech) {
		if (!StringUtils.hasText(speech)) {
			return false;
		}
		String normalized = speech.toLowerCase();
		return TOOL_ACTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
	}

}
