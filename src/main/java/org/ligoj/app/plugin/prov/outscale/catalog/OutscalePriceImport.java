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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
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

	private static final Pattern SQL_SERVER_PATTERN = Pattern.compile(".*(SQL Server[^(]*).*");
	private static final Pattern BYOL_PATTERN = Pattern.compile(".*VDA.*");
	private static final Pattern TERM_PATTERN = Pattern.compile(".*_([hmy][^_]+ly).*");
	private static final Pattern MIN_CPU_PATTERN = Pattern.compile(".*\\s+([0-9]+)\\s+c[^\\s]+\\s+min.*");
	private static final Pattern INCR_CPU_PATTERN = Pattern.compile(".*_([0-9]+)cores.*");

	protected static final TypeReference<Map<String, Term>> MAP_TERMS = new TypeReference<>() {
		// Nothing to extend
	};

	private String getPricesApi() {
		return configuration.get(CONF_API_PRICES, DEFAULT_API_PRICES);
	}

	@Override
	protected int getWorkload(final ImportCatalogStatus status) {
		return 6; // init + get catalog + vm + db + support+storage
	}

	/**
	 * Install or update prices.
	 *
	 * @param force When <code>true</code>, all cost attributes are update.
	 * @throws IOException When CSV or XML files cannot be read.
	 */
	public void install(final boolean force) throws IOException, URISyntaxException {
		final UpdateContext context = initContext(new UpdateContext(), ProvOutscalePluginResource.KEY, force);
		final var node = context.getNode();

		// Get previous data
		nextStep(node, "initialize");
		context.setValidOs(Pattern.compile(configuration.get(CONF_OS, ".*"), Pattern.CASE_INSENSITIVE));
		context.setValidInstanceType(Pattern.compile(configuration.get(CONF_ITYPE, ".*"), Pattern.CASE_INSENSITIVE));
		context.setValidRegion(Pattern.compile(configuration.get(CONF_REGIONS, ".*")));
		context.getMapRegionToName().putAll(toMap("outscale/regions.json", MAP_LOCATION));
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
			term.getConverters().put(BillingPeriod.HOURLY, term.getPeriod() * context.getHoursMonth());
			if (term.getPeriod() >= 1) {
				term.getConverters().put(BillingPeriod.MONTHLY, (double) term.getPeriod());
			}
			if (term.getPeriod() >= 12) {
				term.getConverters().put(BillingPeriod.YEARLY, term.getPeriod() / 12d);
			}
		});
		context.setCsvTerms(terms);

		// Fetch the remote prices stream and build the price objects
		nextStep(node, "retrieve-catalog");
		buildModel(context, getPricesApi());

		// Instances
		nextStep(node, "install-instances");
		installInstances(context);

		// Storages
		nextStep(node, "install-storages");
		installStorage(context);

		// Support
		nextStep(node, "install-support");
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
			p.setSoftware(licenseToSoftware(p.getDescription()));
			p.setByol(licenseToByol(p.getDescription()));
			p.setBillingPeriod(licenseToBillingPeriod(p.getCode()));
			p.setMinCpu(licenseToMinCpu(p.getDescription()));
			p.setIncrementCpu(licenseToIncrementCpu(p.getCode()));
			p.setBillingPeriods(new ArrayList<>());
		});
		getLicenses(context).forEach(p -> {
			getLicenses(context).filter(p2 -> p2.getOs() == p.getOs()
					&& Objects.equals(p2.getSoftware(), p.getSoftware()) && Objects.equals(p2.getByol(), p.getByol())
					&& p2.getBillingPeriod() != p.getBillingPeriod()).forEach(p2 -> {
						p2.setCode(null); // Ignore this price for the next process
						p.getBillingPeriods().add(p2); // Merge this price into the root price
					});
			p.getBillingPeriods().add(p);
		});

		// Install the specific prices
		installServicePrices(this::installInstancePrice, context, "FCU", "Virtual machines");
	}

	/**
	 * Install the storage types and prices.
	 */
	private void installStorage(final UpdateContext context) {
		// Block storage types
		// Standard
		installBlockStorage(context, "do-block-storage-standard", t -> {
			t.setIops(5000);
			t.setThroughput(200);
			t.setInstanceType("s-%");
		});

		// Optimized
		installBlockStorage(context, "do-block-storage-optimized", t -> {
			t.setIops(7500);
			t.setThroughput(300);
			t.setNotInstanceType("s-%");
			t.setInstanceType("%");
		});

		// Snapshot
		final var ssType = installStorageType(context, "do-snapshot", t -> {
			t.setLatency(Rate.GOOD);
			t.setDurability9(11);
			t.setOptimized(ProvStorageOptimized.DURABILITY);
		});

		installServicePrices(this::installInstancePrice, context, "BSU", "Bloc storage");
		installServicePrices(this::installInstancePrice, context, "OSU", "Object storage");
		context.getRegions().keySet().stream().filter(r -> isEnabledRegion(context, r))
				.forEach(r -> installStoragePrice(context, r, ssType, 0.05, r + "/" + ssType.getCode()));
	}

	private void installBlockStorage(UpdateContext context, final String code, final Consumer<ProvStorageType> filler) {
		final var type = installStorageType(context, code, t -> {
			filler.accept(t);
			t.setLatency(Rate.GOOD);
			t.setMaximal(16 * 1024d); // 16TiB
			t.setOptimized(ProvStorageOptimized.IOPS);
		});
		context.getRegions().keySet().stream().filter(r -> isEnabledRegion(context, r))
				.forEach(region -> installStoragePrice(context, region, type, 0.1, region + "/" + type.getCode()));
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
			t.setMinimal(1);
			t.setIncrement(null);
			t.setAvailability(99d);
			aType.accept(t);
		}, stRepository);
	}

	/**
	 * Install or update a storage price.
	 */
	private void installStoragePrice(final UpdateContext context, final String region, final ProvStorageType type,
			final double cost, final String code) {
		final var price = context.getPreviousStorage().computeIfAbsent(code, c -> {
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
				.flatMap(e -> e.getValue().stream()).filter(p -> p.getCode() != null);
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
		if (licence.contains("Microsoft") || licence.contains("Windows")) {
			return VmOs.WINDOWS;
		}
		if (licence.contains("Oracle")) {
			return VmOs.ORACLE;
		}
		if (licence.contains("Red Hat")) {
			return VmOs.RHEL;
		}

		// Fallback to Linux
		return VmOs.LINUX;
	}

	/**
	 * Return the software from the license.
	 */
	protected String licenseToSoftware(final String licence) {
		final var matches = SQL_SERVER_PATTERN.matcher(licence);
		if (matches.find()) {
			return matches.group(1);
		}
		return null;
	}

	/**
	 * Indicate the license is associated to BYOL
	 */
	protected String licenseToByol(final String licence) {
		final var matches = BYOL_PATTERN.matcher(licence);
		if (matches.find()) {
			return "BYOL";
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
			return Integer.parseInt(matches.group(1), 10);
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
			return Double.valueOf(matches.group(1));
		}
		// Not a per-core price
		return null;
	}

	/**
	 * Install a new instance price as needed.
	 */
	private void installInstancePrice(final UpdateContext context, final CsvPrice price, final ProvLocation region,
			final Double cpuCost) {
		final var type = installInstanceType(context, price);
		final var costRam = context.getCostRam().getRegions().getOrDefault(region.getName(), 0d);
		final var dRate = context.getDedicated().getRegions().getOrDefault(region.getName(), 0d) + 1d;
		if (type == null) {
			// Unsupported type, or invalid row -> ignore
			return;
		}

		// Iterate over Outscale contracts (term)
		context.getCsvTerms().values().forEach(csvTerm -> {
			final var term = csvTerm.getEntity();
			final var tRate = context.getHoursMonth() * csvTerm.getRate();
			final var tCpuCost = cpuCost * tRate;
			final var tRamCost = costRam * tRate;
			final var dtCpuCost = tCpuCost * dRate;
			final var dtRamCost = tRamCost * dRate;
			final var converters = csvTerm.getConverters();

			// Linux prices
			installInstancePrice(context, region, term, type, 0d, tCpuCost, tRamCost, ProvTenancy.SHARED, price);
			installInstancePrice(context, region, term, type, 0d, dtCpuCost, dtRamCost, ProvTenancy.DEDICATED, price);

			// License prices
			getLicenses(context).forEach(l -> {
				final var billingPeriods = BillingPeriod.values();
				final var billingIndex = ArrayUtils.indexOf(billingPeriods, csvTerm.getBillingPeriod());
				for (int i = billingIndex; i < billingPeriods.length; i++) {
					final var bPeriod = billingPeriods[i];
					final var pt = l.getBillingPeriods().stream().filter(
							t -> t.getBillingPeriod() == bPeriod && t.getRegions().containsKey(region.getName()))
							.findFirst().orElse(null);
					if (pt != null) {
						// Best license term match found
						final var lrCost = pt.getRegions().get(region.getName()) * tRate * converters.get(bPeriod);
						final Double ltCpuCost;
						final Double ltCost;
						if (l.getIncrementCpu() == null) {
							// Per VM pricing
							ltCpuCost = 0d;
							ltCost = lrCost;
						} else {
							// Per core pricing
							ltCpuCost = lrCost / l.getIncrementCpu();
							ltCost = 0d;
						}
						installInstancePrice(context, region, term, type, ltCost, tCpuCost + ltCpuCost, tRamCost,
								ProvTenancy.SHARED, pt);
						installInstancePrice(context, region, term, type, ltCost, dtCpuCost + ltCpuCost, dtRamCost,
								ProvTenancy.DEDICATED, pt);
						break;
					}
				}
			});
		});
	}

	private void installInstancePrice(final UpdateContext context, final ProvLocation region,
			final ProvInstancePriceTerm term, final ProvInstanceType type, final Double monthlyCost,
			final Double cpuCost, final Double ramCost, final ProvTenancy tenancy, final CsvPrice csvpPrice) {
		final var os = csvpPrice.getOs();
		final var codeParts = new ArrayList<String>(
				List.of(region.getName(), term.getCode(), os.name(), type.getCode(), tenancy.name()));
		if (csvpPrice.getByol() != null) {
			codeParts.add(csvpPrice.getByol());
		}
		if (csvpPrice.getSoftware() != null) {
			codeParts.add(csvpPrice.getSoftware());
		}
		final var price = context.getPrevious().computeIfAbsent(String.join(",", codeParts).toLowerCase(), code -> {
			// New instance price (not update mode)
			final var newPrice = new ProvInstancePrice();
			newPrice.setCode(code);
			return newPrice;
		});
		copyAsNeeded(context, price, p -> {
			p.setLocation(region);
			p.setOs(os);
			p.setTerm(term);
			p.setTenancy(ProvTenancy.SHARED);
			p.setType(type);
			p.setPeriod(term.getPeriod());
		});

		// Update the cost
		saveAsNeeded(context, price, monthlyCost, ipRepository);

		// Cleanup
		getLicenses(context).forEach(p -> p.getBillingPeriods().clear());
	}

	private static final Pattern TINA_EXL_PATTERN = Pattern.compile("c_fcu_vcorev([1-9]+)_(a-z)+");

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
		final var code = "tinav" + gen + ".cXrY." + opt;

		// Only enabled types
		if (isEnabledType(context, code)) {
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
			t.setName(code);
			t.setCpu(0d); // Dynamic
			t.setRam(0); // Dynamic
			t.setDescription(aType.getDescription());
			t.setConstant(!"medium".equals(opt));
			t.setAutoScale(false);

			// See
			// https://wiki.outscale.net/display/FR/Types+d%27instances#Typesd'instances-ProcessorFamiliesG%C3%A9n%C3%A9rationsdeprocesseuretfamillesdeprocesseurcorrespondantes
			switch (gen) {
			case 2 -> t.setProcessor("Intel Xeon Skylake");
			case 3 -> t.setProcessor("Intel Xeon Haswell");
			case 4 -> t.setProcessor("Intel Xeon Broadwell");
			case 5 -> t.setProcessor("Intel Xeon Skylake");
			}

			// Rating
			switch (opt) {
			case "medium" -> t.setCpuRate(getRate(Rate.MEDIUM, gen));
			case "high" -> t.setCpuRate(getRate(Rate.GOOD, gen));
			case "highest" -> t.setCpuRate(getRate(Rate.BEST, gen));
			}

			// Rating
			t.setRamRate(getRate(Rate.MEDIUM, gen));
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
	protected Rate getRate(final Rate rate, final int gen) {
		// Downgrade the rate for a previous generation
		return Rate.values()[Math.max(0, rate.ordinal() - (5 - gen))];
	}

	/**
	 * Install a new price term as needed and complete the specifications.
	 */
	protected ProvInstancePriceTerm installPriceTerm(final UpdateContext context, final String code, final int period) {
		final var term = context.getPriceTerms().computeIfAbsent(code, t -> {
			final var newTerm = new ProvInstancePriceTerm();
			newTerm.setNode(context.getNode());
			newTerm.setCode(t);
			return newTerm;
		});

		// Complete the specifications
		return copyAsNeeded(context, term, t -> {
			t.setName(code /* human readable name */);
			t.setPeriod(period);
			t.setReservation(code.startsWith("RI"));
			t.setConvertibleFamily(false);
			t.setConvertibleType(false);
			t.setConvertibleLocation(false);
			t.setConvertibleOs(true);
			t.setEphemeral(false);
		}, iptRepository);
	}

	public void installSupportPrice(final UpdateContext context, final String code, final ProvSupportPrice aPrice) {
		final var price = context.getPreviousSupport().computeIfAbsent(code, c -> {
			// New instance price
			final ProvSupportPrice newPrice = new ProvSupportPrice();
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
		type.setDescription(aType.getDescription());
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
