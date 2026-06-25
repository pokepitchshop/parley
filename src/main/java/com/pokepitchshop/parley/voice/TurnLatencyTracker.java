package com.pokepitchshop.parley.voice;

public interface TurnLatencyTracker {

	void markLlmFirstToken();

	void markLlmComplete();
}
