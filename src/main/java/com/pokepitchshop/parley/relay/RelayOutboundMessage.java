package com.pokepitchshop.parley.relay;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelayOutboundMessage(
		String type,
		String token,
		@JsonProperty("last") boolean lastToken,
		Boolean interruptible) {

	public static RelayOutboundMessage text(String token, boolean last) {
		return new RelayOutboundMessage("text", token, last, true);
	}
}
