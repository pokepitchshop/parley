package com.pokepitchshop.parley.guardrails;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OutOfScopeDetector {

	private static final List<Pattern> OFF_TOPIC_PATTERNS = List.of(
			Pattern.compile("\\b(weather|forecast|temperature)\\b"),
			Pattern.compile("\\b(tell me a joke|make me laugh|funny story)\\b"),
			Pattern.compile("\\b(write (me )?(a )?(poem|story|essay|code))\\b"),
			Pattern.compile("\\b(medical|doctor|diagnos|symptom|prescription)\\b"),
			Pattern.compile("\\b(legal advice|lawyer|sue|lawsuit)\\b"),
			Pattern.compile("\\b(financial advice|invest|stock market|crypto)\\b"),
			Pattern.compile("\\b(homework|math problem|solve this equation)\\b"),
			Pattern.compile("\\b(politics|election|president|democrat|republican)\\b"));

	public Optional<String> cannedDecline(String speech) {
		if (!StringUtils.hasText(speech)) {
			return Optional.empty();
		}
		String normalized = speech.toLowerCase();
		for (Pattern pattern : OFF_TOPIC_PATTERNS) {
			if (pattern.matcher(normalized).find()) {
				return Optional.of(AgentGuardrails.OFF_TOPIC_DECLINE.trim());
			}
		}
		return Optional.empty();
	}

}
