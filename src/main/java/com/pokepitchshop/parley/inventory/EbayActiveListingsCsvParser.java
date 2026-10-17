package com.pokepitchshop.parley.inventory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Parses the eBay Seller Hub → Reports → "All active listings" CSV (POK-480). Columns are
 * mapped by header name, so column order does not matter. Bad rows are skipped and reported,
 * never fatal; a file missing a required column is rejected outright.
 */
@Component
public class EbayActiveListingsCsvParser {

	static final String ITEM_NUMBER = "Item number";

	static final String TITLE = "Title";

	static final String SKU = "Custom label (SKU)";

	static final String AVAILABLE_QUANTITY = "Available quantity";

	static final String START_PRICE = "Start price";

	static final String CURRENT_PRICE = "Current price";

	static final String CATEGORY = "eBay category 1 name";

	static final String CONDITION = "Condition";

	static final String GRADER = "CD:Professional Grader - (ID: 27501)";

	static final String GRADE = "CD:Grade - (ID: 27502)";

	static final String CARD_CONDITION = "CD:Card Condition - (ID: 40001)";

	private static final List<String> REQUIRED_HEADERS = List.of(ITEM_NUMBER, TITLE, CURRENT_PRICE, AVAILABLE_QUANTITY);

	private static final Pattern PARENTHESIZED = Pattern.compile("\\(([^)]+)\\)\\s*$");

	private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.setTrim(true)
			.get();

	public ParsedListings parse(Reader reader, Instant importedAt) {
		try (CSVParser csv = CSVParser.parse(skipByteOrderMark(reader), FORMAT)) {
			Map<String, Integer> headers = csv.getHeaderMap();
			List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !headers.containsKey(h)).toList();
			if (!missing.isEmpty()) {
				throw new IllegalArgumentException("Not an eBay active listings export; missing columns " + missing);
			}
			List<InventoryItem> items = new ArrayList<>();
			List<SkippedRow> skipped = new ArrayList<>();
			for (CSVRecord row : csv) {
				long rowNumber = row.getRecordNumber() + 1;
				String itemNumber = value(row, ITEM_NUMBER);
				if (itemNumber == null) {
					skipped.add(new SkippedRow(rowNumber, "missing item number"));
					continue;
				}
				String rawPrice = value(row, CURRENT_PRICE) != null ? value(row, CURRENT_PRICE) : value(row, START_PRICE);
				BigDecimal price = parsePrice(rawPrice);
				if (price == null) {
					skipped.add(new SkippedRow(rowNumber, "unparseable price '" + rawPrice + "'"));
					continue;
				}
				Integer quantity = parseQuantity(value(row, AVAILABLE_QUANTITY));
				if (quantity == null) {
					skipped.add(new SkippedRow(rowNumber,
							"unparseable quantity '" + value(row, AVAILABLE_QUANTITY) + "'"));
					continue;
				}
				items.add(new InventoryItem(itemNumber, value(row, TITLE), value(row, SKU), value(row, CATEGORY),
						price, quantity, condition(row), InventoryItem.STATUS_ACTIVE, importedAt));
			}
			return new ParsedListings(List.copyOf(items), List.copyOf(skipped));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read listings CSV", ex);
		}
	}

	/** "PSA 9" for graded cards, else the card condition, else eBay's generic condition. */
	private static String condition(CSVRecord row) {
		String grader = value(row, GRADER);
		String grade = value(row, GRADE);
		if (grader != null && grade != null) {
			Matcher shortName = PARENTHESIZED.matcher(grader);
			return (shortName.find() ? shortName.group(1) : grader) + " " + grade;
		}
		String cardCondition = value(row, CARD_CONDITION);
		return cardCondition != null ? cardCondition : value(row, CONDITION);
	}

	private static String value(CSVRecord row, String header) {
		if (!row.isMapped(header) || !row.isSet(header)) {
			return null;
		}
		String value = row.get(header);
		return StringUtils.hasText(value) ? value : null;
	}

	private static BigDecimal parsePrice(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return new BigDecimal(raw.replace("$", "").replace(",", "").trim());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Integer parseQuantity(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return Integer.valueOf(raw.trim());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/** Seller Hub exports start with a UTF-8 BOM, which would otherwise end up in the first header name. */
	private static Reader skipByteOrderMark(Reader reader) throws IOException {
		BufferedReader buffered = new BufferedReader(reader);
		buffered.mark(1);
		if (buffered.read() != '﻿') {
			buffered.reset();
		}
		return buffered;
	}

	public record ParsedListings(List<InventoryItem> items, List<SkippedRow> skipped) {
	}

	/** @param rowNumber line number in the file, header = 1 */
	public record SkippedRow(long rowNumber, String reason) {
	}

}
