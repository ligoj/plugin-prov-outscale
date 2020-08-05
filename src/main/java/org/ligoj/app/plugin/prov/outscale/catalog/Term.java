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
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Term {

	/**
	 * Hourly, monthly, ...
	 */
	private BillingPeriod billingPeriod;
	private String code;
	
	/**
	 * Period in month.
	 */
	private int period;
	private double rate;

	/**
	 * Resolved price term entity
	 */
	private ProvInstancePriceTerm entity;
	
	/**
	 * Rates table to convert a cost associated to a given {@link BillingPeriod} to this term exprimed in month.
	 */
	private Map<BillingPeriod, Double> converters = new EnumMap<>(BillingPeriod.class);
}
