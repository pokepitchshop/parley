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
		return publicUrl.isConfigured() ? publicUrl.base().trim() : null;
	}
}
