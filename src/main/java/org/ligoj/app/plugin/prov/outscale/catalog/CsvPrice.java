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
public class CsvPrice extends DescribedBean<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;
	@Getter
	@Setter
	private String service;
	@Getter
	@Setter
	private String type;
	@Getter
	@Setter
	private String code;

	// eu-west-2
	@Getter
	@Setter
	private Double regionEUW2;

	// cloudgouv-eu-west-1
	@Getter
	@Setter
	private Double regionSEC1;

	// us-west-1
	@Getter
	@Setter
	private Double regionUSW1;

	// us-east-2
	@Getter
	@Setter
	private Double regionUSE2;

	// cn-southeast-1
	@Getter
	@Setter
	private Double regionCNSE1;

	/**
	 * Regional prices.
	 */
	@Getter
	private final Map<String, Double> regions = new HashedMap<>();

	/**
	 * Resolved OS. May be <code>null</code>.
	 */
	@Getter
	@Setter
	private VmOs os = VmOs.LINUX;

	/**
	 * Resolved software. May be <code>null</code>.
	 */
	@Getter
	@Setter
	private String software;

	/**
	 * Minimal required CPU.
	 */
	@Getter
	@Setter
	private int minCpu;

	/**
	 * Resolved term name: 'hour', 'month' or 'year'
	 */
	@Getter
	@Setter
	private BillingPeriod billingPeriod;
	/**
	 * ISO constraints but the billing period prices.
	 */
	@Getter
	@Setter
	private List<CsvPrice> billingPeriods;

	/**
	 * Incremental CPU. Only for per-core pricing.
	 */
	@Getter
	@Setter
	private Double incrementCpu;

	/**
	 * Ignored property
	 */
	@Setter
	private String drop;
}
