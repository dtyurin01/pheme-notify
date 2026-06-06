package com.pheme.phemenotify;

import com.pheme.phemenotify.messaging.retry.FailedNotificationRetryScheduler;
import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @MockitoBean
    FailedNotificationRetryScheduler retryScheduler;

    @SuppressWarnings("deprecation")
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("pheme_test")
            .withUsername("pheme")
            .withPassword("pheme");

    static final RedisContainer redis =
        new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @SuppressWarnings("deprecation")
    static final KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    static final GenericContainer<?> mailpit =
        new GenericContainer<>("axllent/mailpit")
            .withExposedPorts(1025, 8025);

    static {
        Startables.deepStart(postgres, redis, kafka, mailpit).join();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }
}
