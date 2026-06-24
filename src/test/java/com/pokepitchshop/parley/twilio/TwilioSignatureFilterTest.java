package com.pokepitchshop.parley.twilio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class TwilioSignatureFilterTest {

	@Mock
	TwilioSignatureValidator signatureValidator;

	@Mock
	FilterChain filterChain;

	@InjectMocks
	TwilioSignatureFilter filter;

	@Test
	void rejectsInvalidSignatureOnVoicePath() throws Exception {
		when(signatureValidator.validateHttpRequest(any())).thenReturn(false);
		var request = new MockHttpServletRequest("POST", "/voice/relay");
		var response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(filterChain, never()).doFilter(any(), any());
	}

	@Test
	void allowsValidSignatureOnVoicePath() throws Exception {
		when(signatureValidator.validateHttpRequest(any())).thenReturn(true);
		var request = new MockHttpServletRequest("POST", "/voice/status");
		var response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void skipsValidationOnNonVoicePaths() throws Exception {
		var request = new MockHttpServletRequest("GET", "/health");
		var response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(signatureValidator, never()).validateHttpRequest(any());
		verify(filterChain).doFilter(request, response);
	}
}
