package com.pokepitchshop.parley.twilio;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import com.pokepitchshop.parley.config.ParleyProperties;
import com.pokepitchshop.parley.config.TwilioProperties;
import com.pokepitchshop.parley.relay.ConversationRelayPaths;
import com.twilio.security.RequestValidator;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TwilioSignatureValidator {

	static final String SIGNATURE_HEADER = "X-Twilio-Signature";

	private final TwilioProperties twilioProperties;

	private final ParleyProperties parleyProperties;

	public TwilioSignatureValidator(TwilioProperties twilioProperties, ParleyProperties parleyProperties) {
		this.twilioProperties = twilioProperties;
		this.parleyProperties = parleyProperties;
	}

	public boolean isValidationEnabled() {
		return twilioProperties.isSignatureValidationEnabled();
	}

	public boolean validateHttpRequest(HttpServletRequest request) {
		if (!isValidationEnabled()) {
			return true;
		}
		String signature = request.getHeader(SIGNATURE_HEADER);
		String url = buildHttpUrl(request);
		Map<String, String> params = formParameters(request);
		return validate(url, params, signature);
	}

	public boolean validateWebSocketUpgrade(URI requestUri, HttpHeaders headers) {
		if (!isValidationEnabled()) {
			return true;
		}
		String signature = headers.getFirst(SIGNATURE_HEADER);
		String url = buildWebSocketUrl(requestUri);
		Map<String, String> params = queryParameters(requestUri);
		return validate(url, params, signature);
	}

	public String relayWebSocketUrl() {
		String base = configuredPublicBaseUrl();
		if (!StringUtils.hasText(base)) {
			throw new IllegalStateException(
					"parley.public-url.base (PUBLIC_BASE_URL) must be set to build the ConversationRelay WebSocket URL");
		}
		return toWebSocketBase(base) + ConversationRelayPaths.RELAY_PATH;
	}

	boolean validate(String url, Map<String, String> params, String signature) {
		if (!StringUtils.hasText(signature)) {
			log.warn("Rejected Twilio request with missing {}", SIGNATURE_HEADER);
			return false;
		}
		boolean valid = new RequestValidator(twilioProperties.authToken()).validate(url, params, signature);
		if (!valid) {
			log.warn("Rejected Twilio request with invalid signature for url={}", url);
		}
		return valid;
	}

	String buildHttpUrl(HttpServletRequest request) {
		String configured = configuredPublicBaseUrl();
		if (StringUtils.hasText(configured)) {
			return stripTrailingSlash(configured) + request.getRequestURI();
		}
		String scheme = firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme());
		String host = firstNonBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
		return scheme + "://" + host + request.getRequestURI();
	}

	String buildWebSocketUrl(URI requestUri) {
		String configured = configuredPublicBaseUrl();
		if (StringUtils.hasText(configured)) {
			return toWebSocketBase(configured) + requestUri.getRawPath();
		}
		String scheme = "wss";
		String host = requestUri.getHost();
		if (host == null) {
			throw new IllegalArgumentException("WebSocket URI must include a host: " + requestUri);
		}
		int port = requestUri.getPort();
		String authority = port > 0 ? host + ":" + port : host;
		return scheme + "://" + authority + requestUri.getRawPath();
	}

	private String configuredPublicBaseUrl() {
		return parleyProperties.publicBaseUrlOrNull();
	}

	private static Map<String, String> formParameters(HttpServletRequest request) {
		Map<String, String> params = new LinkedHashMap<>();
		request.getParameterMap().forEach((key, values) -> {
			if (values != null && values.length > 0) {
				params.put(key, values[0]);
			}
		});
		return params;
	}

	static Map<String, String> queryParameters(URI uri) {
		if (!StringUtils.hasText(uri.getRawQuery())) {
			return Collections.emptyMap();
		}
		MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
		for (String pair : uri.getRawQuery().split("&")) {
			int idx = pair.indexOf('=');
			if (idx >= 0) {
				query.add(pair.substring(0, idx), pair.substring(idx + 1));
			}
			else {
				query.add(pair, "");
			}
		}
		return query.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst(), (a, b) -> b, LinkedHashMap::new));
	}

	private static String toWebSocketBase(String baseUrl) {
		String trimmed = stripTrailingSlash(baseUrl.trim());
		if (trimmed.startsWith("https://")) {
			return "wss://" + trimmed.substring("https://".length());
		}
		if (trimmed.startsWith("http://")) {
			return "ws://" + trimmed.substring("http://".length());
		}
		if (trimmed.startsWith("wss://") || trimmed.startsWith("ws://")) {
			return trimmed;
		}
		return "wss://" + trimmed;
	}

	private static String stripTrailingSlash(String value) {
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value;
			}
		}
		return null;
	}
}
