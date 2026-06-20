package com.pokepitchshop.parley.caller;

import java.time.Instant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.pokepitchshop.parley.transcript.CallSummaryService;
import com.pokepitchshop.parley.transcript.Transcript;

@Service
public class CallerService {

	static final String NAME_EXTRACTION_SYSTEM = """
			From this phone call transcript, if the caller clearly stated their name, \
			reply with only that name (first name is fine). Otherwise reply with NONE.
			""";

	private final CallerRepository callerRepository;

	private final ChatClient summaryChatClient;

	public CallerService(CallerRepository callerRepository, @Qualifier("summaryChatClient") ChatClient summaryChatClient) {
		this.callerRepository = callerRepository;
		this.summaryChatClient = summaryChatClient;
	}

	public CallerContext contextFor(String phoneNumber) {
		if (!StringUtils.hasText(phoneNumber)) {
			return CallerContext.anonymous();
		}
		return callerRepository.findById(phoneNumber.trim())
				.map(caller -> new CallerContext(caller.getDisplayName(), caller.getLastSummary()))
				.orElse(CallerContext.anonymous());
	}

	public void updateAfterCall(Transcript transcript, String summary) {
		if (!StringUtils.hasText(transcript.getFromNumber()) || !StringUtils.hasText(summary)) {
			return;
		}
		String phoneNumber = transcript.getFromNumber().trim();
		Caller caller = callerRepository.findById(phoneNumber).orElseGet(() -> new Caller(phoneNumber));
		caller.setLastSummary(summary.trim());
		caller.setLastCallAt(Instant.now());
		String extractedName = extractDisplayName(transcript);
		if (StringUtils.hasText(extractedName)) {
			caller.setDisplayName(extractedName.trim());
		}
		callerRepository.save(caller);
	}

	private String extractDisplayName(Transcript transcript) {
		if (transcript.getTurns().isEmpty()) {
			return null;
		}
		String response = summaryChatClient.prompt()
				.system(NAME_EXTRACTION_SYSTEM)
				.user(CallSummaryService.formatTranscript(transcript))
				.call()
				.content();
		if (!StringUtils.hasText(response) || "NONE".equalsIgnoreCase(response.trim())) {
			return null;
		}
		return response.trim();
	}

}
