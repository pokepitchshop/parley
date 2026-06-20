package com.pokepitchshop.parley.transcript;

import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.pokepitchshop.parley.caller.CallerService;

@Service
public class CallSummaryService {

	private final TranscriptService transcriptService;

	private final ChatClient summaryChatClient;

	private final CallerService callerService;

	public CallSummaryService(
			TranscriptService transcriptService,
			@Qualifier("summaryChatClient") ChatClient summaryChatClient,
			CallerService callerService) {
		this.transcriptService = transcriptService;
		this.summaryChatClient = summaryChatClient;
		this.callerService = callerService;
	}

	@Async
	public void onCallCompleted(String callSid) {
		if (!StringUtils.hasText(callSid)) {
			return;
		}
		transcriptService.markCompleted(callSid);
		var transcript = transcriptService.findByCallSid(callSid).orElse(null);
		if (transcript == null || transcript.getTurns().isEmpty() || StringUtils.hasText(transcript.getSummary())) {
			return;
		}
		String summary = summaryChatClient.prompt()
				.user(formatTranscript(transcript))
				.call()
				.content();
		transcriptService.saveSummary(callSid, summary);
		callerService.updateAfterCall(transcript, summary);
	}

	public static String formatTranscript(Transcript transcript) {
		return transcript.getTurns().stream()
				.map(turn -> "Caller: " + turn.caller() + "\nAgent: " + turn.agent())
				.collect(Collectors.joining("\n\n"));
	}

}
