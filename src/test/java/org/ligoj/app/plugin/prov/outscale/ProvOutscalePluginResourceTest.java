/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
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
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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
		configuration.put("service:prov:digitalocenan:api", "http://localhost:" + MOCK_PORT + "/");
		this.subscription = getSubscription("gStack");

		// Invalidate digitalocean cache
		cacheManager.getCache("curl-tokens").clear();
	}

	@Test
	void getKey() {
		Assertions.assertEquals("service:prov:digitalocean", resource.getKey());
	}

	@Test
	void install() throws Exception {
		final ProvOutscalePluginResource resource2 = new ProvOutscalePluginResource();
		resource2.priceImport = Mockito.mock(OutscalePriceImport.class);
		resource2.install();
	}

	@Test
	void updateCatalog() throws Exception {
		// Re-Install a new configuration
		final var resource2 = new ProvOutscalePluginResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource2);
		resource2.priceImport = Mockito.mock(OutscalePriceImport.class);
		resource2.updateCatalog("service:prov:digitalocean:test", false);
	}

	@Test
	void updateCatalogNoRight() {
		initSpringSecurityContext("any");

		// Re-Install a new configuration
		Assertions.assertEquals("read-only-node", Assertions.assertThrows(BusinessException.class, () -> {
			resource.updateCatalog("service:prov:digitalocean:test", false);
		}).getMessage());
	}

	@Test
	void create() throws Exception {
		prepareMockAuth();
		resource.create(subscription);
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is only one subscription for a service.
	 */
	private int getSubscription(final String project) {
		return getSubscription(project, ProvOutscalePluginResource.KEY);
	}

	private void prepareMockAuth() throws IOException {
		configuration.put(ProvOutscalePluginResource.CONF_API_URL, "http://localhost:" + MOCK_PORT);
		httpServer.stubFor(get(urlEqualTo("/projects"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/digitalocean/projects.json").getInputStream(), "UTF-8"))));
		httpServer.start();
	}

	@Test
	void checkStatus() throws Exception {
		prepareMockAuth();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	/**
	 * Authority error, client side
	 */
	@Test
	void checkStatusAuthorityError() {
		configuration.put(ProvOutscalePluginResource.CONF_API_URL, "http://localhost:" + MOCK_PORT);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), ProvOutscalePluginResource.PARAMETER_TOKEN, "digitalocean-login");
	}

	@Test
	void getVersion() throws Exception {
		Assertions.assertEquals("2", resource.getVersion(subscription));
	}

}
