package com.pokepitchshop.parley.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.pokepitchshop.parley.twilio.TwilioSignatureValidator;

@ExtendWith(MockitoExtension.class)
class TwilioWebSocketHandshakeInterceptorTest {

	@Mock
	TwilioSignatureValidator signatureValidator;

	@InjectMocks
	TwilioWebSocketHandshakeInterceptor interceptor;

	@Test
	void rejectsHandshakeWhenSignatureInvalid() {
		when(signatureValidator.validateWebSocketUpgrade(any(), any())).thenReturn(false);
		var servletResponse = new MockHttpServletResponse();
		var request = serverRequest("/relay");
		var response = new ServletServerHttpResponse(servletResponse);

		boolean accepted = interceptor.beforeHandshake(request, response, null, new HashMap<>());

		assertThat(accepted).isFalse();
		assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
	}

	@Test
	void acceptsHandshakeWhenSignatureValid() {
		when(signatureValidator.validateWebSocketUpgrade(any(), any())).thenReturn(true);
		var request = serverRequest("/relay");
		var response = new ServletServerHttpResponse(new MockHttpServletResponse());

		boolean accepted = interceptor.beforeHandshake(request, response, null, new HashMap<>());

		assertThat(accepted).isTrue();
	}

	private static ServletServerHttpRequest serverRequest(String path) {
		return new ServletServerHttpRequest(new MockHttpServletRequest("GET", path));
	}
}
