package com.pokepitchshop.parley.voice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parley.voice")
public class VoiceProperties {

	/**
	 * Twilio neural voice for {@code <Say>} (e.g. Polly.Joanna-Neural).
	 */
	private String sayVoice = "Polly.Joanna-Neural";

	/**
	 * Seconds of silence after the caller stops speaking before Twilio posts {@code SpeechResult}.
	 */
	private int speechTimeout = 3;

	public String getSayVoice() {
		return sayVoice;
	}

	public void setSayVoice(String sayVoice) {
		this.sayVoice = sayVoice;
	}

	public int getSpeechTimeout() {
		return speechTimeout;
	}

	public void setSpeechTimeout(int speechTimeout) {
		this.speechTimeout = speechTimeout;
	}

}
