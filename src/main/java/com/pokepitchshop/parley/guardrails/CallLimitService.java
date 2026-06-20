package com.pokepitchshop.parley.guardrails;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.pokepitchshop.parley.transcript.TranscriptService;
import com.pokepitchshop.parley.voice.VoiceProperties;

@Service
public class CallLimitService {

	private final TranscriptService transcriptService;

	private final VoiceProperties voiceProperties;

	private final ConcurrentHashMap<String, AtomicInteger> toolCallsByCall = new ConcurrentHashMap<>();

	public CallLimitService(TranscriptService transcriptService, VoiceProperties voiceProperties) {
		this.transcriptService = transcriptService;
		this.voiceProperties = voiceProperties;
	}

	public boolean hasReachedTurnLimit(String callSid) {
		return countTurns(callSid) >= voiceProperties.getMaxTurnsPerCall();
	}

	public int countTurns(String callSid) {
		if (!StringUtils.hasText(callSid)) {
			return 0;
		}
		return transcriptService.findByCallSid(callSid)
				.map(transcript -> transcript.getTurns().size())
				.orElse(0);
	}

	public boolean canInvokeTool(String callSid) {
		if (!StringUtils.hasText(callSid)) {
			return false;
		}
		return toolCallCount(callSid) < voiceProperties.getMaxToolCallsPerCall();
	}

	public void recordToolCall(String callSid) {
		if (!StringUtils.hasText(callSid)) {
			return;
		}
		toolCallsByCall.computeIfAbsent(callSid, ignored -> new AtomicInteger(0)).incrementAndGet();
	}

	public void clearCall(String callSid) {
		if (!StringUtils.hasText(callSid)) {
			return;
		}
		toolCallsByCall.remove(callSid);
	}

	private int toolCallCount(String callSid) {
		AtomicInteger count = toolCallsByCall.get(callSid);
		return count == null ? 0 : count.get();
	}

}
