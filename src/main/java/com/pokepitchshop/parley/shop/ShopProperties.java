package com.pokepitchshop.parley.shop;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "parley.shop")
public record ShopProperties(
		@DefaultValue("Poke Pitch Shop") String storeName,
		@DefaultValue("Poke Pitch Shop") String ebaySellerName,
		@DefaultValue("We ship trading cards nationwide through eBay with careful packaging.")
				String shippingBlurb,
		@DefaultValue("Local pickup may be available by arrangement — ask and we can check.")
				String pickupBlurb,
		@DefaultValue("Sound like a friendly collector who runs a small eBay shop. Mention eBay when callers want to browse or buy.")
				String toneHint) {
}
