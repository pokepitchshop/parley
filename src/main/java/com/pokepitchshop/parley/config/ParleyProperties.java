package com.pokepitchshop.parley.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "parley")
public record ParleyProperties(@DefaultValue PublicUrl publicUrl) {

	public ParleyProperties {
		if (publicUrl == null) {
			publicUrl = new PublicUrl("");
		}
	}

	public record PublicUrl(@DefaultValue("") String base) {

		public boolean isConfigured() {
			return base != null && !base.isBlank();
		}
	}

	public String publicBaseUrlOrNull() {
		if (!publicUrl.isConfigured()) {
			return null;
		}
		String base = stripTrailingSlash(publicUrl.base().trim());
		while (base.endsWith("/voice")) {
			base = stripTrailingSlash(base.substring(0, base.length() - "/voice".length()));
		}
		return base;
	}

	private static String stripTrailingSlash(String value) {
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}
}
