/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale.catalog;

import java.util.List;
import java.util.Map;

import org.ligoj.app.plugin.prov.catalog.AbstractUpdateContext;

import lombok.Getter;
import lombok.Setter;

/**
 * Context used to perform catalog update.
 */
@Getter
@Setter
public class UpdateContext extends AbstractUpdateContext {

	// Specific Context

	/**
	 * Terms as defined by Outscale
	 */
	private Map<String, Term> csvTerms;

	/**
	 * Cost RAM.
	 */
	private CsvPrice costRam;
	
	/**
	 * Dedicated tenancy cost.
	 */
	private CsvPrice dedicated;

	/**
	 * All CSV costs.
	 */
	private Map<String, Map<String, List<CsvPrice>>> csvPrices;

}
