package com.pokepitchshop.parley.voice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpokenSentenceChunkerTest {

	@Test
	void drainsCompleteSentencesFromTokenStream() {
		var chunker = new SpokenSentenceChunker();

		assertThat(chunker.appendAndDrainSentences("We are open. ")).containsExactly("We are open.");
		assertThat(chunker.appendAndDrainSentences("We close at eight.")).containsExactly("We close at eight.");
		assertThat(chunker.flushRemainder()).isEmpty();
	}

	@Test
	void keepsPartialSentenceInBufferUntilPunctuation() {
		var chunker = new SpokenSentenceChunker();

		assertThat(chunker.appendAndDrainSentences("We are open")).isEmpty();
		assertThat(chunker.appendAndDrainSentences(" until six.")).containsExactly("We are open until six.");
	}

	@Test
	void flushRemainderReturnsTextWithoutSentenceEnd() {
		var chunker = new SpokenSentenceChunker();

		assertThat(chunker.appendAndDrainSentences("Yes")).isEmpty();
		assertThat(chunker.flushRemainder()).contains("Yes");
	}

	@Test
	void doesNotSplitDecimalNumbers() {
		var chunker = new SpokenSentenceChunker();

		assertThat(chunker.appendAndDrainSentences("It costs 3.50 today.")).containsExactly("It costs 3.50 today.");
	}
}
