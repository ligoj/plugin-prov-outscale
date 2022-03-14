/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.model.DelegateNode;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.outscale.catalog.OutscalePriceImport;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvOutscalePluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class ProvOutscalePluginResourceTest extends AbstractServerTest {

	protected int subscription;

	@Autowired
	private ProvOutscalePluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ConfigurationResource configuration;

	@BeforeEach
	void prepareData() throws IOException {
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, CacheCompany.class, CacheUser.class, DelegateNode.class,
						Subscription.class, ProvLocation.class, ProvQuote.class, Parameter.class,
						ParameterValue.class },
				StandardCharsets.UTF_8.name());
		configuration.put("service:prov:outscale:api", "http://localhost:" + MOCK_PORT + "/");
		this.subscription = getSubscription("gStack");

		// Invalidate outscale cache
		cacheManager.getCache("curl-tokens").clear();
	}

	@Test
	void getKey() {
		Assertions.assertEquals("service:prov:outscale", resource.getKey());
	}

	@Test
	void getName() {
		Assertions.assertEquals("3DS Outscale", resource.getName());
	}

	@Test
	void install() throws Exception {
		final var resource2 = new ProvOutscalePluginResource();
		resource2.priceImport = Mockito.mock(OutscalePriceImport.class);
		resource2.install();
	}

	@Test
	void updateCatalog() throws Exception {
		// Re-Install a new configuration
		final var resource2 = new ProvOutscalePluginResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource2);
		resource2.priceImport = Mockito.mock(OutscalePriceImport.class);
		resource2.updateCatalog("service:prov:outscale:test", false);
	}

	@Test
	void updateCatalogNoRight() {
		initSpringSecurityContext("any");

		// Re-Install a new configuration
		Assertions.assertEquals("read-only-node", Assertions.assertThrows(BusinessException.class, () -> {
			resource.updateCatalog("service:prov:outscale:test", false);
		}).getMessage());
	}

	@Test
	void create() throws Exception {
		resource.create(subscription);
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is only one subscription for a service.
	 */
	private int getSubscription(final String project) {
		return getSubscription(project, ProvOutscalePluginResource.KEY);
	}

	@Test
	void checkStatus() throws Exception {
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void getVersion() throws Exception {
		Assertions.assertEquals("2", resource.getVersion(subscription));
	}

}
