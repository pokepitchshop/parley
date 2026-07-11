package com.pokepitchshop.parley.shop;

public record StoreFacts(
		String storeName,
		String ebaySellerName,
		String shippingBlurb,
		String pickupBlurb,
		String toneHint) {

	public static StoreFacts from(ShopProperties properties) {
		return new StoreFacts(
				properties.storeName(),
				properties.ebaySellerName(),
				properties.shippingBlurb(),
				properties.pickupBlurb(),
				properties.toneHint());
	}
}
