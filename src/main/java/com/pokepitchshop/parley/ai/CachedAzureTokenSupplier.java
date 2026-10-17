package com.pokepitchshop.parley.ai;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;

/**
 * Bearer tokens for Azure OpenAI straight from a {@link TokenCredential}, reused until
 * {@link #REFRESH_MARGIN} before expiry so most LLM turns make no auth call at all.
 */
class CachedAzureTokenSupplier implements Supplier<String> {

	static final String COGNITIVE_SERVICES_SCOPE = "https://cognitiveservices.azure.com/.default";

	/** Fetch a new token this long before the cached one expires. */
	static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

	private final TokenCredential credential;

	private final Clock clock;

	CachedAzureTokenSupplier(TokenCredential credential, Clock clock) {
		this.credential = credential;
		this.clock = clock;
	}

	private AccessToken cached;

	@Override
	public synchronized String get() {
		if (cached == null || !clock.instant().isBefore(cached.getExpiresAt().toInstant().minus(REFRESH_MARGIN))) {
			cached = credential.getTokenSync(new TokenRequestContext().addScopes(COGNITIVE_SERVICES_SCOPE));
		}
		return cached.getToken();
	}

}
