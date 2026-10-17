package com.pokepitchshop.parley.inventory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.ParsedListings;
import com.pokepitchshop.parley.inventory.EbayActiveListingsCsvParser.SkippedRow;
import com.pokepitchshop.parley.inventory.InventoryImportService.ImportSummary;

/**
 * Entry point for {@code scripts/import-inventory.sh}: only loaded under the {@code import}
 * profile (no web server), imports one Seller Hub CSV, prints the summary, then exits.
 */
@Component
@Profile("import")
public class InventoryImportRunner implements ApplicationRunner {

	private final EbayActiveListingsCsvParser parser;

	private final InventoryImportService importService;

	private final ConfigurableApplicationContext context;

	public InventoryImportRunner(
			EbayActiveListingsCsvParser parser,
			InventoryImportService importService,
			ConfigurableApplicationContext context) {
		this.parser = parser;
		this.importService = importService;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		List<String> files = args.getNonOptionArgs();
		if (files.size() != 1) {
			System.err.println("Usage: scripts/import-inventory.sh <active-listings.csv> [--dry-run]");
			System.exit(SpringApplication.exit(context, () -> 2));
		}
		Path csv = Path.of(files.get(0));
		boolean dryRun = args.containsOption("dry-run");

		ParsedListings parsed;
		try (Reader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
			parsed = parser.parse(reader, Instant.now());
		}
		ImportSummary summary = importService.apply(parsed, csv.getFileName().toString(), Instant.now(), dryRun);

		System.out.printf("%s%s: added=%d updated=%d ended=%d skipped=%d%n",
				dryRun ? "[dry run] " : "", csv.getFileName(), summary.added(), summary.updated(), summary.ended(),
				summary.skipped().size());
		for (SkippedRow row : summary.skipped()) {
			System.out.printf("  skipped row %d: %s%n", row.rowNumber(), row.reason());
		}
		System.exit(SpringApplication.exit(context, () -> 0));
	}

}
