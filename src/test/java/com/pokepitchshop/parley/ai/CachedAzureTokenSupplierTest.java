package com.pokepitchshop.parley.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;

@ExtendWith(MockitoExtension.class)
class CachedAzureTokenSupplierTest {

	private static final Instant NOW = Instant.parse("2026-10-17T20:00:00Z");

	@Mock
	private TokenCredential credential;

	private CachedAzureTokenSupplier sut;

	@BeforeEach
	void setUp() {
		sut = new CachedAzureTokenSupplier(credential, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void fetchesCognitiveServicesTokenFromCredential() {
		given(credential.getTokenSync(argThat(CachedAzureTokenSupplierTest::isCognitiveServicesScope)))
				.willReturn(token("first", NOW.plusSeconds(3600)));

		assertThat(sut.get()).isEqualTo("first");
	}

	@Test
	void reusesTokenUntilItNearsExpiry() {
		given(credential.getTokenSync(argThat(CachedAzureTokenSupplierTest::isCognitiveServicesScope)))
				.willReturn(token("first", NOW.plusSeconds(3600)), token("second", NOW.plusSeconds(7200)));

		sut.get();

		assertThat(sut.get()).isEqualTo("first");
	}

	@Test
	void refreshesTokenWithinFiveMinutesOfExpiry() {
		given(credential.getTokenSync(argThat(CachedAzureTokenSupplierTest::isCognitiveServicesScope)))
				.willReturn(token("first", NOW.plusSeconds(300)), token("second", NOW.plusSeconds(3600)));

		sut.get();

		assertThat(sut.get()).isEqualTo("second");
	}

	@Test
	void keepsTokenJustOutsideTheRefreshWindow() {
		given(credential.getTokenSync(argThat(CachedAzureTokenSupplierTest::isCognitiveServicesScope)))
				.willReturn(token("first", NOW.plusSeconds(301)), token("second", NOW.plusSeconds(3600)));

		sut.get();

		assertThat(sut.get()).isEqualTo("first");
	}

	private static boolean isCognitiveServicesScope(TokenRequestContext context) {
		return context.getScopes().equals(List.of(CachedAzureTokenSupplier.COGNITIVE_SERVICES_SCOPE));
	}

	private static AccessToken token(String value, Instant expiresAt) {
		return new AccessToken(value, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
	}

}
