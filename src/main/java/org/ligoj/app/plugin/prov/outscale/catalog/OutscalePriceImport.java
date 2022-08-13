/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.catalog.AbstractImportCatalogResource;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.app.plugin.prov.outscale.ProvOutscalePluginResource;
import org.ligoj.bootstrap.core.INamableBean;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * The provisioning price service for Digital Ocean. Manage install or update of prices.<br>
 *
 * @see <a href="hhttps://en.outscale.com/pricing/">Pricing details</a>
 * @see <a href="hhttps://en.outscale.com/pricing/how-pricing-works/">Pricing computation</a>
 * @see <a href="https://wiki.outscale.net/display/EN/Getting+the+Price+of+Your+Instances">Reservation</a>
 * @see <a href="https://wiki.outscale.net/pages/viewpage.action?pageId=43061335">Locations</a>
 * @see <a href="https://fr.outscale.com/support-technique/">Support</a>
 * @see <a href="https://wiki.outscale.net/pages/viewpage.action?pageId=43066330">Instance Types</a>
 * @see <a href="https://wiki.outscale.net/display/EN/About+Volumes">Volumes</a>
 * @see <a href="https://wiki.outscale.net/display/FR/Types+d'instances">Instance types</a>
 *
 */
@Component
@Setter
@Slf4j
public class OutscalePriceImport extends AbstractImportCatalogResource {

	/**
	 * Configuration key used for URL prices.
	 */
	protected static final String CONF_API_PRICES = ProvOutscalePluginResource.KEY + ":prices-url";

	/**
	 * Configuration key used for enabled regions pattern names. When value is <code>null</code>, no restriction.
	 */
	protected static final String CONF_REGIONS = ProvOutscalePluginResource.KEY + ":regions";

	/**
	 * Default pricing URL.
	 */
	protected static final String DEFAULT_API_PRICES = "https://outscale.ligoj.io";

	/**
	 * Name space for local configuration files
	 */
	protected static final String PREFIX = "outscale";

	/**
	 * Configuration key used for enabled instance type pattern names. When value is <code>null</code>, no restriction.
	 */
	public static final String CONF_ITYPE = ProvOutscalePluginResource.KEY + ":instance-type";

	/**
	 * Configuration key used for enabled OS pattern names. When value is <code>null</code>, no restriction.
	 */
	public static final String CONF_OS = ProvOutscalePluginResource.KEY + ":os";

	private static final Pattern SQL_SERVER_PATTERN = Pattern.compile(".*(SQL Server.*)\\s+Edition.*");
	private static final Pattern TERM_PATTERN = Pattern.compile(".*_([hmy][^_]+ly).*");
	private static final Pattern MIN_CPU_PATTERN = Pattern.compile(".*\\s+([0-9]+)\\s+c[^\\s]+\\s+min.*");
	private static final Pattern INCR_CPU_PATTERN = Pattern.compile(".*_([0-9]+)cores.*");
	private static final Pattern TINA_EXL_PATTERN = Pattern.compile("c_fcu_vcorev([0-9]+)_([a-z]+)");

	protected static final TypeReference<Map<String, Term>> MAP_TERMS = new TypeReference<>() {
		// Nothing to extend
	};

	private String getPricesApi() {
		return configuration.get(CONF_API_PRICES, DEFAULT_API_PRICES);
	}

	@Override
	protected int getWorkload(final ImportCatalogStatus status) {
		return 5; // init + get catalog + vm + support+storage
	}

	/**
	 * Install or update prices.
	 *
	 * @param force When <code>true</code>, all cost attributes are update.
	 * @throws IOException When CSV or XML files cannot be read.
	 */
	public void install(final boolean force) throws IOException, URISyntaxException {
		final var context = initContext(new UpdateContext(), ProvOutscalePluginResource.KEY, force);
		final var node = context.getNode();

		// Get previous data
		nextStep(context, "initialize");
		context.setValidOs(Pattern.compile(configuration.get(CONF_OS, ".*"), Pattern.CASE_INSENSITIVE));
		context.setValidInstanceType(Pattern.compile(configuration.get(CONF_ITYPE, ".*"), Pattern.CASE_INSENSITIVE));
		context.setValidRegion(Pattern.compile(configuration.get(CONF_REGIONS, ".*")));
		context.getMapRegionById().putAll(toMap("outscale/regions.json", MAP_LOCATION));
		context.setInstanceTypes(itRepository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvInstanceType::getCode, Function.identity())));
		context.setPriceTerms(iptRepository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvInstancePriceTerm::getCode, Function.identity())));
		context.setStorageTypes(stRepository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvStorageType::getCode, Function.identity())));
		context.setPreviousStorage(spRepository.findAllBy("type.node", node).stream()
				.collect(Collectors.toMap(ProvStoragePrice::getCode, Function.identity())));
		context.setSupportTypes(st2Repository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvSupportType::getName, Function.identity())));
		context.setPreviousSupport(sp2Repository.findAllBy("type.node", node).stream()
				.collect(Collectors.toMap(ProvSupportPrice::getCode, Function.identity())));
		context.setRegions(locationRepository.findAllBy(BY_NODE, context.getNode()).stream()
				.filter(r -> isEnabledRegion(context, r))
				.collect(Collectors.toMap(INamableBean::getName, Function.identity())));
		context.setPrevious(ipRepository.findAllBy("term.node", node).stream()
				.collect(Collectors.toMap(ProvInstancePrice::getCode, Function.identity())));
		// Term definitions
		final var terms = toMap("outscale/terms.json", MAP_TERMS);
		terms.entrySet().forEach(e -> {
			final var term = e.getValue();
			term.setEntity(installPriceTerm(context, e.getKey(), term.getPeriod()));
			term.getConverters().put(BillingPeriod.HOURLY, Math.max(1d, term.getPeriod()) * context.getHoursMonth());
			if (term.getPeriod() >= 1) {
				term.getConverters().put(BillingPeriod.MONTHLY, Math.max(1d, term.getPeriod()));
			}
			if (term.getPeriod() >= 12) {
				term.getConverters().put(BillingPeriod.YEARLY, Math.max(1d, term.getPeriod()) / 12d);
			}
		});
		context.setCsvTerms(terms);

		// Fetch the remote prices stream and build the price objects
		nextStep(context, "retrieve-catalog");
		buildModel(context, StringUtils.removeEnd(getPricesApi(), "/") + "/prices/outscale-prices.csv");

		// Instances
		nextStep(context, "install-instances");
		installInstances(context);

		// Storages
		nextStep(context, "install-storages");
		installStorage(context);

		// Support
		nextStep(context, "install-support");
		csvForBean.toBean(ProvSupportType.class, PREFIX + "/prov-support-type.csv").forEach(t -> {
			installSupportType(context, t.getCode(), t);
		});
		csvForBean.toBean(ProvSupportPrice.class, PREFIX + "/prov-support-price.csv").forEach(t -> {
			installSupportPrice(context, t.getCode(), t);
		});
	}

	/**
	 * Install instances
	 */
	private void installInstances(final UpdateContext context) {

		// Extract the RAM cost
		context.setCostRam(getPrices(context, "FCU", "Virtual machines").filter(p -> "c_fcu_ram".equals(p.getCode()))
				.findFirst().orElse(new CsvPrice()));

		// Extract the dedicated cost
		context.setDedicated(getPrices(context, "FCU", "Virtual machines")
				.filter(p -> "c_fcu_dedicated_vm_extra_hourly".equals(p.getCode())).findFirst().orElse(new CsvPrice()));

		// Compute license and software
		getLicenses(context).forEach(p -> {
			p.setOs(licenseToVmOs(p.getCode()));
			p.setSoftware(licenseToSoftware(p.getName()));
			p.setBillingPeriod(licenseToBillingPeriod(p.getCode()));
			p.setMinCpu(licenseToMinCpu(p.getName()));
			p.setIncrementCpu(licenseToIncrementCpu(p.getCode()));
			p.setBillingPeriods(new ArrayList<>());
		});
		getLicenses(context).forEach(p -> {
			getLicenses(context)
					.filter(p2 -> p2.getOs() == p.getOs() && Objects.equals(p2.getSoftware(), p.getSoftware())
							&& p2.getBillingPeriod() != p.getBillingPeriod())
					.forEach(p2 -> {
						p2.setCode(null); // Ignore this price for the next process
						p.getBillingPeriods().add(p2); // Merge this price into the root price
					});
			// Also add its billing period
			p.getBillingPeriods().add(p);
		});

		// Install the specific prices
		installServicePrices(this::installInstancePrices, context, "FCU", "Virtual machines");
	}

	/**
	 * Install the storage types and prices.
	 *
	 * @see <a href="https://wiki.outscale.net/display/EN/About+Volumes">Volume types</a>
	 */
	private void installStorage(final UpdateContext context) {
		// Block storage types
		// Magnetic
		installStorageType(context, "bsu-standard", t -> {
			t.setName("Magnetic");
			t.setLatency(Rate.GOOD);
			t.setIops(400);
			t.setThroughput(40);
			t.setInstanceType("%");
		});

		// Performance
		installStorageType(context, "bsu-gp2", t -> {
			t.setName("Performance");
			t.setLatency(Rate.BEST);
			t.setOptimized(ProvStorageOptimized.IOPS);
			t.setIops(10000);
			t.setThroughput(160);
			t.setInstanceType("%");
		});

		// Enterprise
		installStorageType(context, "bsu-io1", t -> {
			t.setName("Enterprise");
			t.setLatency(Rate.BEST);
			t.setOptimized(ProvStorageOptimized.IOPS);
			t.setIops(10000);
			t.setThroughput(200);
			t.setInstanceType("%");
			t.setMinimal(4d);
		});

		// Snapshot
		installStorageType(context, "bsu-snapshot", t -> {
			t.setName("Snapshot");
			t.setLatency(Rate.LOW);
			t.setDurability9(11);
			t.setOptimized(ProvStorageOptimized.DURABILITY);
			t.setIncrement(null);
			t.setAvailability(99d);
			t.setMaximal(null);
		});

		// Object storage type
		// Enterprise
		// 1 Site, 3 replicas
		installStorageType(context, "osu-enterprise", t -> {
			t.setName("OSU Enterprise");
			t.setLatency(Rate.MEDIUM);
			t.setDurability9(11);
			t.setOptimized(ProvStorageOptimized.DURABILITY);
			t.setIncrement(null);
			t.setAvailability(99d);
			t.setMinimal(0d);
			t.setMaximal(null);
		});

		// Object storage type
		// Premium
		// 2 Sites, 6 replicas
		installStorageType(context, "osu-premium", t -> {
			t.setName("OSU Premium");
			t.setLatency(Rate.MEDIUM);
			t.setDurability9(11);
			t.setOptimized(ProvStorageOptimized.DURABILITY);
			t.setIncrement(null);
			t.setAvailability(99d);
			t.setMinimal(0d);
			t.setMaximal(null);
		});

		installServicePrices(this::installStoragePrices, context, "BSU", "Bloc storage");
		installServicePrices(this::installStoragePrices, context, "OSU", "Object storage");
	}

	/**
	 * Install or update a storage type.
	 */
	private ProvStorageType installStorageType(final UpdateContext context, final String code,
			final Consumer<ProvStorageType> aType) {
		final var type = context.getStorageTypes().computeIfAbsent(code, c -> {
			final var newType = new ProvStorageType();
			newType.setNode(context.getNode());
			newType.setCode(c);
			return newType;
		});

		return copyAsNeeded(context, type, t -> {
			t.setName(code /* human readable name */);
			t.setIncrement(null);
			t.setAvailability(99d);
			t.setMaximal(14901d);
			t.setMinimal(1d);
			aType.accept(t);
		}, stRepository);
	}

	/**
	 * Install or update a storage price.
	 */
	private void installStoragePrice(final UpdateContext context, final String region, final ProvStorageType type,
			final double cost) {
		final var price = context.getPreviousStorage().computeIfAbsent(region + "/" + type.getCode(), c -> {
			final var newPrice = new ProvStoragePrice();
			newPrice.setType(type);
			newPrice.setCode(c);
			return newPrice;
		});

		copyAsNeeded(context, price, p -> {
			p.setLocation(installRegion(context, region));
			p.setType(type);
		});

		// Update the cost
		saveAsNeeded(context, price, cost, spRepository);
	}

	/**
	 * Add a regional price in CSV model.
	 */
	private void addRegionalPrice(final CsvPrice csv, final String region, final Double cost) {
		if (cost != null) {
			csv.getRegions().put(region, cost);
		}
	}

	/**
	 * Download the remote CSV catalog, normalize data and build the in-memory model.
	 */
	private void buildModel(final UpdateContext context, final String endpoint) throws IOException, URISyntaxException {
		// Track the created instance to cache partial costs
		log.info("Outscale OnDemand/Reserved import started@{} ...", endpoint);

		// Get the remote prices stream
		var prices = new HashMap<String, Map<String, List<CsvPrice>>>();
		try (var reader = new BufferedReader(new InputStreamReader(new URI(endpoint).toURL().openStream()))) {
			// Pipe to the CSV reader
			final var csvReader = new CsvForBeanOutscale(reader);

			// Build the AWS instance prices from the CSV
			var csv = csvReader.read();
			while (csv != null) {

				// Make regional prices more readable
				addRegionalPrice(csv, "eu-west-2", csv.getRegionEUW2());
				addRegionalPrice(csv, "cloudgouv-eu-west-1", csv.getRegionSEC1());
				addRegionalPrice(csv, "us-west-1", csv.getRegionUSW1());
				addRegionalPrice(csv, "us-east-2", csv.getRegionUSE2());
				addRegionalPrice(csv, "cn-southeast-1", csv.getRegionCNSE1());

				// Add to the prices map
				prices.computeIfAbsent(csv.getService(), k -> new HashMap<>())
						.computeIfAbsent(csv.getType(), k -> new ArrayList<>()).add(csv);

				// Read the next one
				csv = csvReader.read();
			}
		} finally {
			// Report
			log.info("Outscale OnDemand/Reserved import finished: {} prices ({})", context.getPrices().size(),
					String.format("%+d", context.getPrices().size()));
		}

		// Store the prices in context
		context.setCsvPrices(prices);
	}

	private Stream<CsvPrice> getLicenses(final UpdateContext context) {
		return context.getCsvPrices().getOrDefault("Licences", Collections.emptyMap()).entrySet().stream()
				.flatMap(e -> e.getValue().stream().filter(l -> !l.getType().equals("Windows 10")))
				.filter(p -> p.getCode() != null);
	}

	private Stream<CsvPrice> getPrices(final UpdateContext context, final String service, final String type) {
		return getPrices(context.getCsvPrices(), service, type);
	}

	private Stream<CsvPrice> getPrices(final Map<String, Map<String, List<CsvPrice>>> prices, final String service,
			final String type) {
		return prices.getOrDefault(service, Collections.emptyMap()).getOrDefault(type, Collections.emptyList())
				.stream();
	}

	private void installServicePrices(final QuadConsumer<UpdateContext, CsvPrice, ProvLocation, Double> installer,
			final UpdateContext context, final String service, final String type) {
		getPrices(context.getCsvPrices(), service, type)
				.forEach(v -> v.getRegions().entrySet().stream().filter(e -> isEnabledRegion(context, e.getKey()))
						.forEach(e -> installer.accept(context, v, installRegion(context, e.getKey()), e.getValue())));
	}

	/**
	 * Return the OS from the license.
	 */
	protected VmOs licenseToVmOs(final String licence) {
		if (licence.contains("oracle")) {
			return VmOs.ORACLE;
		}
		if (licence.contains("rhel")) {
			return VmOs.RHEL;
		}

		// Fallback to Windows
		return VmOs.WINDOWS;
	}

	/**
	 * Return the software from the license.
	 */
	protected String licenseToSoftware(final String licence) {
		final var matches = SQL_SERVER_PATTERN.matcher(licence);
		if (matches.find()) {
			// Clean the software name
			return matches.group(1).toUpperCase(Locale.ENGLISH).replace("STD", "STANDARD").trim();
		}
		return null;
	}

	/**
	 * Indicate the license is associated to a specific term name.
	 */
	protected BillingPeriod licenseToBillingPeriod(final String licence) {
		final var matches = TERM_PATTERN.matcher(licence);
		if (matches.find()) {
			return EnumUtils.getEnum(BillingPeriod.class, matches.group(1).toUpperCase(), BillingPeriod.HOURLY);
		}
		// Default is hourly
		return BillingPeriod.HOURLY;
	}

	/**
	 * Indicate the license is associated to a minimum vCPU counts.
	 */
	protected int licenseToMinCpu(final String licence) {
		final var matches = MIN_CPU_PATTERN.matcher(licence);
		if (matches.find()) {
			return Integer.parseInt(matches.group(1).trim(), 10);
		}
		// Default is 0
		return 0;
	}

	/**
	 * Indicate the license is associated to a minimum vCPU counts.
	 */
	protected Double licenseToIncrementCpu(final String licence) {
		final var matches = INCR_CPU_PATTERN.matcher(licence);
		if (matches.find()) {
			return Double.valueOf(matches.group(1).trim());
		}
		// Not a per-core price
		return null;
	}

	private void installStoragePrices(final UpdateContext context, final CsvPrice price, final ProvLocation region,
			final Double costGb) {
		final var typeParts = price.getCode().split("_");
		final var last = typeParts[typeParts.length - 1];
		final var service = price.getService().toLowerCase();
		List.of(last, service + "-" + last, service + "-" + last.replace("std", "standard")).stream()
				.map(context.getStorageTypes()::get).filter(Objects::nonNull).findFirst()
				.ifPresent(type -> installStoragePrice(context, region.getName(), type, costGb));
	}

	/**
	 * Install a new instance price as needed.
	 */
	private void installInstancePrices(final UpdateContext context, final CsvPrice price, final ProvLocation region,
			final Double cpuCost) {
		final var type = installInstanceType(context, price);
		if (type == null) {
			// Unsupported type, or invalid row -> ignore
			return;
		}

		final var costRam = context.getCostRam().getRegions().getOrDefault(region.getName(), 0d);
		final var dRate = context.getDedicated().getRegions().getOrDefault(region.getName(), 0d) + 1d;
		// Iterate over Outscale contracts (terms)
		context.getCsvTerms().values()
				.forEach(t -> installInstancePrices(context, price, region, cpuCost, t, costRam, dRate, type));
	}

	/**
	 * Install a new instance price as needed.
	 */
	private void installInstancePrices(final UpdateContext context, final CsvPrice price, final ProvLocation region,
			final double cpuCost, final Term csvTerm, final double costRam, final double dedicatedRate,
			final ProvInstanceType type) {
		final var term = csvTerm.getEntity();
		final var tRate = context.getHoursMonth() * csvTerm.getRate();
		final var tCpuCost = cpuCost * tRate;
		final var tRamCost = costRam * tRate;
		final var dtCpuCost = tCpuCost * dedicatedRate;
		final var dtRamCost = tRamCost * dedicatedRate;

		// Linux prices
		installInstancePrice(context, region, term, type, 0d, tCpuCost, tRamCost, ProvTenancy.SHARED, price);
		installInstancePrice(context, region, term, type, 0d, dtCpuCost, dtRamCost, ProvTenancy.DEDICATED, price);

		// For each OS only price
		getLicenses(context).filter(l -> l.getSoftware() == null).map(CsvPrice::getOs).distinct()
				.map(os -> getClosestBilling(context, c -> c.getSoftware() == null, os, region, csvTerm))
				.filter(Objects::nonNull).forEach(osPrice -> {
					// Get the best OS billing period
					final var osCost = getCost(osPrice, region, csvTerm);
					final double osCpuCost;
					final double osVmCost;
					if (osPrice.getIncrementCpu() == null) {
						// Per VM pricing
						osVmCost = osCost;
						osCpuCost = 0d;
					} else {
						// Per core pricing
						osVmCost = 0d;
						osCpuCost = osCost / osPrice.getIncrementCpu();
					}

					// Install compute+OS prices
					installInstancePrice(context, region, term, type, osVmCost, tCpuCost + osCpuCost, tRamCost,
							ProvTenancy.SHARED, osPrice);
					installInstancePrice(context, region, term, type, osVmCost, dtCpuCost + osCpuCost, dtRamCost,
							ProvTenancy.DEDICATED, osPrice);

					// Add the software price for each OS
					getLicenses(context).filter(l -> l.getSoftware() != null).filter(l -> l.getOs() == osPrice.getOs())
							.map(CsvPrice::getSoftware).distinct().map(s -> getClosestBilling(context,
									c -> s.equals(c.getSoftware()), osPrice.getOs(), region, csvTerm))
							.filter(Objects::nonNull).forEach(sPrice -> {
								final var sCost = getCost(sPrice, region, csvTerm);
								final var sCpuCost = sCost / sPrice.getIncrementCpu();
								installInstancePrice(context, region, term, type, osVmCost,
										tCpuCost + osCpuCost + sCpuCost, tRamCost, ProvTenancy.SHARED, sPrice);
								installInstancePrice(context, region, term, type, osVmCost,
										dtCpuCost + osCpuCost + sCpuCost, dtRamCost, ProvTenancy.DEDICATED, sPrice);
							});
				});

	}

	private double getCost(final CsvPrice price, final ProvLocation region, final Term csvTerm) {
		return price.getRegions().get(region.getName()) * csvTerm.getConverters().get(price.getBillingPeriod());
	}

	/**
	 * Return the first price matching to the OS/Region requirement and having a closest billing period licensing.
	 */
	private CsvPrice getClosestBilling(final UpdateContext context, final Predicate<CsvPrice> filter, final VmOs os,
			final ProvLocation region, final Term csvTerm) {
		final var billingPeriods = BillingPeriod.values();
		final var licenses = getLicenses(context).filter(l -> l.getOs() == os).filter(filter)
				.filter(l -> l.getRegions().containsKey(region.getName())).flatMap(l -> l.getBillingPeriods().stream())
				.toList();
		return Arrays.stream(BillingPeriod.values())
				.skip(ArrayUtils.indexOf(billingPeriods, csvTerm.getBillingPeriod()))
				.flatMap(b -> licenses.stream().filter(l -> l.getBillingPeriod() == b)).findFirst().orElse(null);
	}

	private void installInstancePrice(final UpdateContext context, final ProvLocation region,
			final ProvInstancePriceTerm term, final ProvInstanceType type, final Double monthlyCost,
			final Double cpuCost, final Double ramCost, final ProvTenancy tenancy, final CsvPrice csvpPrice) {
		// Build the code string
		final var os = csvpPrice.getOs();
		final var codeParts = new ArrayList<>(List.of(region.getName(), term.getCode(), os.name(), type.getCode()));
		if (tenancy != ProvTenancy.SHARED) {
			codeParts.add(tenancy.name());
		}
		if (csvpPrice.getSoftware() != null) {
			codeParts.add(csvpPrice.getSoftware());
		}

		final var price = context.getPrevious().computeIfAbsent(String.join("/", codeParts).toLowerCase(), code -> {
			// New instance price (not update mode)
			final var newPrice = new ProvInstancePrice();
			newPrice.setCode(code);
			return newPrice;
		});

		// Save the price as needed
		copyAsNeeded(context, price, p -> {
			p.setLocation(region);
			p.setOs(os);
			p.setTerm(term);
			p.setTenancy(tenancy);
			p.setType(type);
			p.setIncrementCpu(Objects.requireNonNullElse(csvpPrice.getIncrementCpu(), 1d));
			p.setMinCpu((double) csvpPrice.getMinCpu());
			p.setPeriod(term.getPeriod());
			p.setSoftware(csvpPrice.getSoftware());
		});

		// Update the cost
		saveAsNeeded(context, price, Objects.requireNonNullElse(price.getCostCpu(), 0d), cpuCost, (cR, c) -> {
			price.setCostCpu(cR);
			price.setCostRam(round3Decimals(ramCost));
			price.setCost(round3Decimals(monthlyCost));
			price.setCostPeriod(round3Decimals(monthlyCost * Math.max(1, term.getPeriod())));
		}, ipRepository::save);
	}

	/**
	 * Install a new instance type as needed.
	 */
	private ProvInstanceType installInstanceType(final UpdateContext context, final CsvPrice aType) {
		final var matcher = TINA_EXL_PATTERN.matcher(aType.getCode());
		if (!matcher.find()) {
			// Not a valid pattern
			return null;
		}
		final var gen = Integer.parseInt(matcher.group(1), 10);
		final var opt = matcher.group(2);
		final var name = "tinav" + gen + ".cXrY." + opt;
		final var code = name.toLowerCase(Locale.ENGLISH);

		// Only enabled types
		if (!isEnabledType(context, code)) {
			return null;
		}

		final var type = context.getInstanceTypes().computeIfAbsent(code, c -> {
			// New instance type (not update mode)
			final var newType = new ProvInstanceType();
			newType.setNode(context.getNode());
			newType.setCode(c);
			return newType;
		});

		// Merge as needed
		return copyAsNeeded(context, type, t -> {
			t.setName(name);
			t.setCpu(0d); // Dynamic
			t.setRam(0); // Dynamic
			t.setDescription(aType.getName());
			t.setBaseline("medium".equals(opt) ? 20d : 100d);
			t.setAutoScale(false);

			// See
			// https://wiki.outscale.net/display/FR/Types+d%27instances#Typesd'instances-ProcessorFamiliesG%C3%A9n%C3%A9rationsdeprocesseuretfamillesdeprocesseurcorrespondantes
			switch (gen) {
			case 2 -> t.setProcessor("Intel Xeon Skylake");
			case 3 -> t.setProcessor("Intel Xeon Haswell");
			case 4 -> t.setProcessor("Intel Xeon Broadwell");
			case 5 -> t.setProcessor("Intel Xeon Skylake");
			}

			// Rating CPU
			switch (opt) {
			case "medium" -> t.setCpuRate(getRate(Rate.MEDIUM, gen, Rate.WORST));
			case "high" -> t.setCpuRate(getRate(Rate.GOOD, gen, Rate.LOW));
			case "highest" -> t.setCpuRate(getRate(Rate.BEST, gen, Rate.MEDIUM));
			}

			// Rating RAM
			t.setRamRate(t.getCpuRate());

			// Rating
			t.setNetworkRate(Rate.MEDIUM);
			t.setStorageRate(Rate.MEDIUM);
		}, itRepository);
	}

	/**
	 * Return the most precise rate from a base rate and a generation.
	 *
	 * @param rate The base rate.
	 * @param gen  The generation.
	 * @return The adjusted rate. Previous generations types are downgraded.
	 */
	protected Rate getRate(final Rate rate, final int gen, final Rate min) {
		// Downgrade the rate for a previous generation
		return Rate.values()[Math.max(min.ordinal(), rate.ordinal() - (5 - gen))];
	}

	/**
	 * Install a new price term as needed and complete the specifications.
	 */
	protected ProvInstancePriceTerm installPriceTerm(final UpdateContext context, final String name, final int period) {
		final var code = name.toLowerCase(Locale.ENGLISH).replace(" ", "");
		final var term = context.getPriceTerms().computeIfAbsent(code, t -> {
			final var newTerm = new ProvInstancePriceTerm();
			newTerm.setNode(context.getNode());
			newTerm.setCode(t);
			return newTerm;
		});

		// Complete the specifications
		return copyAsNeeded(context, term, t -> {
			t.setName(name);
			t.setPeriod(period);
			t.setReservation(code.startsWith("ri"));
			t.setConvertibleFamily(false);
			t.setConvertibleType(false);
			t.setConvertibleLocation(false);
			t.setConvertibleOs(true);
			t.setEphemeral(false);
		});
	}

	public void installSupportPrice(final UpdateContext context, final String code, final ProvSupportPrice aPrice) {
		final var price = context.getPreviousSupport().computeIfAbsent(code, c -> {
			// New instance price
			final var newPrice = new ProvSupportPrice();
			newPrice.setCode(c);
			return newPrice;
		});

		// Merge the support type details
		copyAsNeeded(context, price, p -> {
			p.setLimit(aPrice.getLimit());
			p.setMin(aPrice.getMin());
			p.setRate(aPrice.getRate());
			p.setType(aPrice.getType());
		});

		// Update the cost
		saveAsNeeded(context, price, price.getCost(), aPrice.getCost(), (cR, c) -> price.setCost(cR),
				sp2Repository::save);
	}

	private ProvSupportType installSupportType(final UpdateContext context, final String code,
			final ProvSupportType aType) {
		final var type = context.getSupportTypes().computeIfAbsent(code, c -> {
			var newType = new ProvSupportType();
			newType.setName(c);
			newType.setCode(c);
			newType.setNode(context.getNode());
			return newType;
		});

		// Merge the support type details
		type.setAccessApi(aType.getAccessApi());
		type.setAccessChat(aType.getAccessChat());
		type.setAccessEmail(aType.getAccessEmail());
		type.setAccessPhone(aType.getAccessPhone());
		type.setSlaStartTime(aType.getSlaStartTime());
		type.setSlaEndTime(aType.getSlaEndTime());
		type.setDescription(aType.getDescription());

		type.setSlaBusinessCriticalSystemDown(aType.getSlaBusinessCriticalSystemDown());
		type.setSlaGeneralGuidance(aType.getSlaGeneralGuidance());
		type.setSlaProductionSystemDown(aType.getSlaProductionSystemDown());
		type.setSlaProductionSystemImpaired(aType.getSlaProductionSystemImpaired());
		type.setSlaSystemImpaired(aType.getSlaSystemImpaired());
		type.setSlaWeekEnd(aType.isSlaWeekEnd());

		type.setCommitment(aType.getCommitment());
		type.setSeats(aType.getSeats());
		type.setLevel(aType.getLevel());
		st2Repository.save(type);
		return type;
	}

}
