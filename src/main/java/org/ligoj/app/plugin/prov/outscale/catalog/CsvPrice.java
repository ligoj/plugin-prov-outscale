/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale.catalog;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.HashedMap;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Catalog entry. The "id" corresponds to the SKU.
 */
@Getter
@Setter
public class CsvPrice extends DescribedBean<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;
	private String service;
	private String type;
	private String code;

	// eu-west-2
	private Double regionEUW2;

	// cloudgouv-eu-west-1
	private double regionSEC1;

	// us-west-1
	private double regionUSW1;

	// us-east-2
	private double regionUSE2;

	// cn-southeast-1
	private double regionCNSE1;

	/**
	 * Regional prices.
	 */
	private Map<String, Double> regions = new HashedMap<>();

	/**
	 * Resolved OS. May be <code>null</code>.
	 */
	private VmOs os = VmOs.LINUX;

	/**
	 * Resolved software. May be <code>null</code>.
	 */
	private String software;

	/**
	 * Resolved BYOL option. May be <code>null</code>.
	 */
	private String byol;

	/**
	 * Minimal required CPU.
	 */
	private int minCpu;

	/**
	 * Resolved term name: 'hour', 'month' or 'year'
	 */
	private BillingPeriod billingPeriod;
	/**
	 * ISO constraints but the billing period prices.
	 */
	private List<CsvPrice> billingPeriods;

	/**
	 * Incremental CPU. Only for per-core pricing.
	 */
	private Double incrementCpu;
}
