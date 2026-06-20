package com.pokepitchshop.parley.caller;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "callers")
@Getter
@Setter
@NoArgsConstructor
public class Caller {

	@Id
	private String phoneNumber;

	private String displayName;

	private String lastSummary;

	private Instant lastCallAt;

	public Caller(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

}
