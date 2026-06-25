package com.pokepitchshop.parley.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class SpokenSentenceChunker {

	private final StringBuilder buffer = new StringBuilder();

	List<String> appendAndDrainSentences(String token) {
		buffer.append(token);
		List<String> sentences = new ArrayList<>();
		int scanFrom = 0;
		while (scanFrom < buffer.length()) {
			int end = findSentenceEnd(buffer, scanFrom);
			if (end < 0) {
				break;
			}
			String sentence = buffer.substring(scanFrom, end).trim();
			scanFrom = end;
			while (scanFrom < buffer.length() && Character.isWhitespace(buffer.charAt(scanFrom))) {
				scanFrom++;
			}
			if (!sentence.isEmpty()) {
				sentences.add(sentence);
			}
		}
		if (scanFrom > 0) {
			buffer.delete(0, scanFrom);
		}
		return sentences;
	}

	Optional<String> flushRemainder() {
		if (buffer.isEmpty()) {
			return Optional.empty();
		}
		String remainder = buffer.toString().trim();
		buffer.setLength(0);
		return remainder.isEmpty() ? Optional.empty() : Optional.of(remainder);
	}

	private static int findSentenceEnd(CharSequence text, int from) {
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != '.' && c != '!' && c != '?') {
				continue;
			}
			if (c == '.' && isDecimalPoint(text, i)) {
				continue;
			}
			return i + 1;
		}
		return -1;
	}

	private static boolean isDecimalPoint(CharSequence text, int index) {
		return index > 0
				&& Character.isDigit(text.charAt(index - 1))
				&& index + 1 < text.length()
				&& Character.isDigit(text.charAt(index + 1));
	}
}
