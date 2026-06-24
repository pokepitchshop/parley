package com.pokepitchshop.parley.voice;

@FunctionalInterface
public interface VoiceReplyChunkConsumer {

	/**
	 * @return {@code false} to stop streaming (e.g. caller interrupted)
	 */
	boolean onChunk(String text, boolean last);
}
