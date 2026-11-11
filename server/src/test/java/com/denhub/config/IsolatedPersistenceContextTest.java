package com.denhub.config;

import com.denhub.domain.Authority;
import jakarta.persistence.EntityManagerFactory;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextCustomizerFactory;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IsolatedPersistenceContextTest {
    // Only ApplicationContextRunner registers this helper; it is not an application component.
    @EntityScan(basePackageClasses = Authority.class)
    static class Entities {
    }

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withInitializer(IsolatedPersistenceContextCustomizerFactory::customize)
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class))
            .withUserConfiguration(Entities.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:${denhub.test.database-name};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa", "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
                    "spring.jpa.properties.hibernate.generate_statistics=true",
                    "spring.jpa.properties.hibernate.javax.cache.missing_cache_strategy=create");

    @Test
    void committedRowsAndLiveCacheSurviveClosingAnotherContext() {
        contexts.run(first -> {
            assertNull(first.getStartupFailure());
            EntityManagerFactory firstJpa = first.getBean(EntityManagerFactory.class);
            var firstManager = first.getBean(EhcacheCachingProvider.class).getCacheManager();
            insertAuthority(firstJpa);
            assertNotNull(readAuthority(firstJpa));
            assertTrue(firstJpa.getCache().contains(Authority.class, "ROLE_USER"));

            contexts.run(second -> {
                assertNull(second.getStartupFailure());
                EntityManagerFactory secondJpa = second.getBean(EntityManagerFactory.class);
                var secondManager = second.getBean(EhcacheCachingProvider.class).getCacheManager();
                var firstJdbc = new JdbcTemplate(first.getBean(DataSource.class));
                var secondJdbc = new JdbcTemplate(second.getBean(DataSource.class));

                assertNotEquals(first.getEnvironment().getProperty("spring.datasource.url"),
                        second.getEnvironment().getProperty("spring.datasource.url"));
                assertNotSame(firstManager, secondManager);
                // A shared database or a shared Authority region would incorrectly return ROLE_USER.
                assertNull(readAuthority(secondJpa));
                assertEquals(0L, secondJdbc.queryForObject("SELECT COUNT(*) FROM authorities", Long.class));
                assertEquals(1L, firstJdbc.queryForObject("SELECT COUNT(*) FROM authorities", Long.class));
                insertAuthority(secondJpa);
                assertNotNull(readAuthority(secondJpa));
                assertTrue(secondJpa.getCache().contains(Authority.class, "ROLE_USER"));

                first.getSourceApplicationContext().close();
                assertTrue(firstManager.isClosed());
                assertFalse(secondManager.isClosed());
                var statistics = secondJpa.unwrap(SessionFactory.class).getStatistics();
                long hits = statistics.getSecondLevelCacheHitCount();
                assertNotNull(readAuthority(secondJpa));
                assertTrue(statistics.getSecondLevelCacheHitCount() > hits,
                        "The surviving context must still use its live second-level cache");
                assertEquals(1L, secondJdbc.queryForObject("SELECT COUNT(*) FROM authorities", Long.class),
                        "Closing the first context must not drop the second context's schema");
            });
        });
    }

    @Test
    void springTestDiscoversIsolationWithoutBreakingContextCacheReuse() {
        var factories = SpringFactoriesLoader.loadFactories(ContextCustomizerFactory.class, getClass().getClassLoader());
        var factory = factories.stream().filter(IsolatedPersistenceContextCustomizerFactory.class::isInstance)
                .findFirst().orElseThrow();
        assertEquals(factory.createContextCustomizer(getClass(), List.of()),
                factory.createContextCustomizer(ChannelMessagingTest.class, List.of()),
                "Isolation must not create a separate Spring context for every test class");
    }

    @Test
    void ordinaryApplicationScanDoesNotDiscoverTestHelpers() {
        // Reproduce an IDE launch whose runtime classpath accidentally includes test output.
        // A normal app scan has no Spring test exclusion filter to hide these classes.
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        var candidates = scanner.findCandidateComponents("com.denhub.config").stream()
                .map(definition -> definition.getBeanClassName()).toList();
        assertTrue(candidates.contains(WebSocketConfig.class.getName()), "The production package must be scanned");
        assertFalse(candidates.contains(ChannelMessagingTest.TestConfig.class.getName()),
                "The messaging mock must never be discovered by an ordinary application scan");
        assertFalse(candidates.contains(IsolatedPersistenceContextCustomizerFactory.IsolatedHibernateCache.class.getName()));
        assertFalse(candidates.contains(Entities.class.getName()));
    }

    private void insertAuthority(EntityManagerFactory jpa) {
        var em = jpa.createEntityManager();
        try {
            em.getTransaction().begin();
            Authority authority = new Authority();
            authority.setName("ROLE_USER");
            em.persist(authority);
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private Authority readAuthority(EntityManagerFactory jpa) {
        var em = jpa.createEntityManager();
        try {
            return em.find(Authority.class, "ROLE_USER");
        } finally {
            em.close();
        }
    }
}
