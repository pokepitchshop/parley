package com.pokepitchshop.parley.transcript;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "transcripts")
@Getter
@Setter
@NoArgsConstructor
public class Transcript {

	@Id
	private String callSid;

	private String fromNumber;

	private Instant startedAt;

	private Instant completedAt;

	private List<Turn> turns = new ArrayList<>();

	private String summary;

	private Instant summarizedAt;

	public Transcript(String callSid, String fromNumber, Instant startedAt) {
		this.callSid = callSid;
		this.fromNumber = fromNumber;
		this.startedAt = startedAt;
	}

}
