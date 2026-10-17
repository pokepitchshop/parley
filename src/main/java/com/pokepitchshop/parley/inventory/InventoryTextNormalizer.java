package com.pokepitchshop.parley.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns listing titles and caller utterances into comparable token sets: lowercase, strip
 * punctuation, fold simple plurals, apply card-speak synonyms, drop stopwords.
 */
final class InventoryTextNormalizer {

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

	/** Filler words plus shopping words that appear in nearly every utterance or title. */
	private static final Set<String> STOPWORDS = Set.of(
			"a", "an", "and", "any", "anything", "are", "can", "card", "do", "for", "get", "got", "have",
			"i", "in", "is", "it", "like", "looking", "me", "my", "of", "on", "or", "some", "sell",
			"that", "the", "there", "this", "to", "want", "what", "with", "you", "your");

	/** Alias phrase (normalized, space-separated) → canonical single token. Longest aliases first. */
	private final List<Synonym> synonyms;

	InventoryTextNormalizer(List<String> synonymGroups) {
		List<Synonym> parsed = new ArrayList<>();
		for (String group : synonymGroups) {
			List<String> terms = Arrays.stream(group.split("\\|"))
					.map(term -> String.join(" ", foldedWords(term)))
					.filter(term -> !term.isEmpty())
					.toList();
			if (terms.size() < 2) {
				continue;
			}
			String canonical = terms.get(0).replace(' ', '_');
			for (String term : terms) {
				parsed.add(new Synonym(term, canonical));
			}
		}
		parsed.sort(Comparator.comparingInt((Synonym s) -> s.alias().length()).reversed());
		this.synonyms = List.copyOf(parsed);
	}

	Set<String> tokens(String text) {
		if (text == null || text.isBlank()) {
			return Set.of();
		}
		String phrase = " " + String.join(" ", foldedWords(text)) + " ";
		for (Synonym synonym : synonyms) {
			phrase = phrase.replace(" " + synonym.alias() + " ", " " + synonym.canonical() + " ");
		}
		Set<String> tokens = new LinkedHashSet<>();
		for (String token : phrase.trim().split(" ")) {
			if (!token.isEmpty() && !STOPWORDS.contains(token)) {
				tokens.add(token);
			}
		}
		return tokens;
	}

	private static List<String> foldedWords(String text) {
		String normalized = NON_ALPHANUMERIC.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
		if (normalized.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(normalized.split(" ")).map(InventoryTextNormalizer::singular).toList();
	}

	/** Folds "rookies" → "rookie", "cards" → "card"; leaves short words and "-ss" words alone. */
	private static String singular(String word) {
		if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
			return word.substring(0, word.length() - 1);
		}
		return word;
	}

	private record Synonym(String alias, String canonical) {
	}

}
