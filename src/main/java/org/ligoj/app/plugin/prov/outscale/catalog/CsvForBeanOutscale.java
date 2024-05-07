/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ligoj.bootstrap.core.csv.AbstractCsvManager;
import org.ligoj.bootstrap.core.csv.CsvBeanReader;
import org.ligoj.bootstrap.core.csv.CsvReader;

/**
 * Read AWS EC2 CSV input, skipping the AWS headers and non instance type rows.
 */
public class CsvForBeanOutscale extends AbstractCsvManager {

	private final CsvBeanReader<CsvPrice> beanReader;

	/**
	 * CSV Mapping to Java bean property
	 */
	protected static final Map<String, String> HEADERS_MAPPING = new HashMap<>();
	static {
		HEADERS_MAPPING.put("Service", "service");
		HEADERS_MAPPING.put("Type", "type");
		HEADERS_MAPPING.put("Description", "name");
		HEADERS_MAPPING.put("eu-west-2", "regionEUW2");
		HEADERS_MAPPING.put("cloudgouv-eu-west-1", "regionSEC1");
		HEADERS_MAPPING.put("us-west-1", "regionUSW1");
		HEADERS_MAPPING.put("us-east-2", "regionUSE2");
		HEADERS_MAPPING.put("cn-southeast-1", "regionCNSE1");
		HEADERS_MAPPING.put("Excel named range for reference", "code");
	}

	/**
	 * Build the reader parsing the CSV file from AWS to build {@link CsvPrice} instances. Non AWS instances data are
	 * skipped, and headers are ignored.
	 *
	 * @param reader The original AWS CSV input.
	 * @throws IOException When CSV content cannot be read.
	 */
	public CsvForBeanOutscale(final BufferedReader reader) throws IOException {

		// Complete the standard mappings
		final var mMapping = new HashMap<>(HEADERS_MAPPING);
		final var csvReader = new CsvReader(reader);

		// The real CSV header has be reached
		this.beanReader = newCsvReader(reader,
				csvReader.read().stream().map(v -> mMapping.getOrDefault(v, "drop")).toArray(String[]::new));
	}

	protected CsvBeanReader<CsvPrice> newCsvReader(final Reader reader, final String[] headers) {
		return new AbstractOutscaleCsvReader<>(reader, headers, CsvPrice.class) {

			@Override
			protected boolean isValidRaw(final List<String> rawValues) {
				return CsvForBeanOutscale.this.isValidRaw(rawValues);
			}

		};
	}

	private boolean isValidRaw(final List<String> rawValues) {
		return rawValues.size() >= 7;
	}

	/**
	 * Return a list of JPA bean re ad from the given CSV input. Headers are expected.
	 *
	 * @return The bean read from the next CSV record.
	 * @throws IOException When the CSV record cannot be read.
	 */
	public CsvPrice read() throws IOException {
		return beanReader.read();
	}
}
