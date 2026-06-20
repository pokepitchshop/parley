package com.pokepitchshop.parley.transcript;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TranscriptService {

	private final TranscriptRepository transcriptRepository;

	public TranscriptService(TranscriptRepository transcriptRepository) {
		this.transcriptRepository = transcriptRepository;
	}

	public void appendTurn(String callSid, String fromNumber, String caller, String agent) {
		if (!StringUtils.hasText(callSid) || !StringUtils.hasText(caller) || !StringUtils.hasText(agent)) {
			return;
		}
		Transcript transcript = transcriptRepository.findById(callSid)
				.orElseGet(() -> new Transcript(callSid, fromNumber, Instant.now()));
		if (StringUtils.hasText(fromNumber)) {
			transcript.setFromNumber(fromNumber);
		}
		transcript.getTurns().add(new Turn(Instant.now(), caller.trim(), agent.trim()));
		transcriptRepository.save(transcript);
	}

	public Optional<Transcript> findByCallSid(String callSid) {
		if (!StringUtils.hasText(callSid)) {
			return Optional.empty();
		}
		return transcriptRepository.findById(callSid);
	}

	public void markCompleted(String callSid) {
		if (!StringUtils.hasText(callSid)) {
			return;
		}
		transcriptRepository.findById(callSid).ifPresent(transcript -> {
			transcript.setCompletedAt(Instant.now());
			transcriptRepository.save(transcript);
		});
	}

	public void saveSummary(String callSid, String summary) {
		if (!StringUtils.hasText(callSid) || !StringUtils.hasText(summary)) {
			return;
		}
		transcriptRepository.findById(callSid).ifPresent(transcript -> {
			transcript.setSummary(summary.trim());
			transcript.setSummarizedAt(Instant.now());
			transcriptRepository.save(transcript);
		});
	}

}
