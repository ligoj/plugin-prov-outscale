/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale.catalog;

import java.util.EnumMap;
import java.util.Map;

import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * A defined term.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Term {

	/**
	 * Hourly, monthly, ...
	 */
	@Getter
	@Setter
	private BillingPeriod billingPeriod;

	/**
	 * Period in month.
	 */
	@Getter
	@Setter
	private int period;

	/**
	 * 1 minus Discount rate
	 */
	@Getter
	@Setter
	private double rate;

	/**
	 * Resolved price term entity
	 */
	@Getter
	@Setter
	private ProvInstancePriceTerm entity;

	/**
	 * Rates table to convert a cost associated to a given {@link BillingPeriod} to this term exprimed in month.
	 */
	@Getter
	private Map<BillingPeriod, Double> converters = new EnumMap<>(BillingPeriod.class);
}
