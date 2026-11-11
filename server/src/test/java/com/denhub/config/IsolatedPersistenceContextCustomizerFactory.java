package com.denhub.config;

import org.ehcache.jsr107.EhcacheCachingProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test-only resources must not be shared by independently cached Spring contexts. */
public class IsolatedPersistenceContextCustomizerFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        // Stable equality preserves Spring's reuse of identical context configurations.
        return IsolationCustomizer.INSTANCE;
    }

    static void customize(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "isolatedTestDatabase", Map.of("denhub.test.database-name", "denhub-test-" + UUID.randomUUID())));
        new AnnotatedBeanDefinitionReader((BeanDefinitionRegistry) context.getBeanFactory())
                .register(IsolatedHibernateCache.class);
    }

    private enum IsolationCustomizer implements ContextCustomizer {
        INSTANCE;

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config) {
            customize(context);
        }
    }

    // Explicitly registered above as a lite @Bean container, never discovered by component scanning.
    static class IsolatedHibernateCache {
        @Bean(destroyMethod = "close")
        EhcacheCachingProvider isolatedTestCachingProvider() {
            // A provider instance owns its own manager registry. Hibernate may close its manager
            // when an EntityManagerFactory stops, without closing another context's live caches.
            return new EhcacheCachingProvider();
        }

        @Bean
        HibernatePropertiesCustomizer isolatedTestHibernateCache(EhcacheCachingProvider provider) {
            return properties -> properties.put("hibernate.javax.cache.cache_manager", provider.getCacheManager());
        }
    }
}
