package com.pokepitchshop.parley.twilio;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import com.pokepitchshop.parley.config.ParleyProperties;
import com.pokepitchshop.parley.config.TwilioProperties;

class TwilioSignatureValidatorTest {

	private static final String AUTH_TOKEN = "12345";

	@Test
	void skipsValidationWhenAuthTokenMissing() {
		var validator = validator("", "https://example.com");

		assertThat(validator.isValidationEnabled()).isFalse();
		assertThat(validator.validateHttpRequest(request("/voice"))).isTrue();
	}

	@Test
	void buildsHttpUrlFromConfiguredPublicBaseUrl() {
		var validator = validator(AUTH_TOKEN, "https://parley.example.com");

		assertThat(validator.buildHttpUrl(request("/voice/respond")))
				.isEqualTo("https://parley.example.com/voice/respond");
	}

	@Test
	void stripsVoiceSuffixFromConfiguredPublicBaseUrl() {
		var validator = validator(AUTH_TOKEN, "https://parley.example.com/voice/");

		assertThat(validator.buildHttpUrl(request("/voice/relay")))
				.isEqualTo("https://parley.example.com/voice/relay");
		assertThat(validator.relayWebSocketUrl()).isEqualTo("wss://parley.example.com/relay");
	}

	@Test
	void buildsWebSocketUrlFromConfiguredPublicBaseUrl() {
		var validator = validator(AUTH_TOKEN, "https://parley.example.com");

		assertThat(validator.relayWebSocketUrl()).isEqualTo("wss://parley.example.com/relay");
	}

	@Test
	void validatesKnownTwilioSignature() {
		var validator = validator(AUTH_TOKEN, null);
		String url = "https://example.com/voice";
		Map<String, String> params = Map.of(
				"CallSid", "CA123",
				"From", "+15551234567",
				"To", "+15559876543");
		String signature = "GnkxvL7WGTelPjgVbd4PpAWKK94=";

		assertThat(validator.validate(url, params, signature)).isTrue();
		assertThat(validator.validate(url, params, "bad-signature")).isFalse();
	}

	@Test
	void validatesWebSocketUpgradeUsingPublicBaseUrl() {
		var validator = validator(AUTH_TOKEN, "https://parley.example.com");
		String url = "wss://parley.example.com/relay";
		String signature = "jBTd1hA2I6eSxafBhwbdnywnLMs=";

		var headers = new HttpHeaders();
		headers.add(TwilioSignatureValidator.SIGNATURE_HEADER, signature);

		assertThat(validator.validateWebSocketUpgrade(URI.create("/relay"), headers)).isTrue();
	}

	private static TwilioSignatureValidator validator(String authToken, String publicBaseUrl) {
		return new TwilioSignatureValidator(
				new TwilioProperties("ACtest", authToken),
				new ParleyProperties(new ParleyProperties.PublicUrl(publicBaseUrl)));
	}

	private static MockHttpServletRequest request(String path) {
		var request = new MockHttpServletRequest("POST", path);
		request.setServerName("localhost");
		request.setServerPort(8080);
		request.setScheme("http");
		return request;
	}
}
