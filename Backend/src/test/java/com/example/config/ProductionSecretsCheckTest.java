package com.example.config;

import com.example.DemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static com.example.config.ProductionSecretsCheck.DEV_JWT_SECRET;
import static com.example.config.ProductionSecretsCheck.DEV_QR_SECRET;
import static com.example.config.ProductionSecretsCheck.DEV_SEED_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A production server must not start on the public development secrets; a
 * laptop must start with no setup at all.
 */
class ProductionSecretsCheckTest {

    private static final String PRIVATE_JWT = "a-private-jwt-secret-of-at-least-32-characters";
    private static final String PRIVATE_QR = "a-private-qr-secret";
    private static final String PRIVATE_SEED = "A-private-seed-1";

    @Test
    void productionRefusesEachDevelopmentDefaultAndNamesItsVariable() {
        assertThatThrownBy(() -> ProductionSecretsCheck.check("production",
                DEV_JWT_SECRET, PRIVATE_QR, PRIVATE_SEED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QCARE_JWT_SECRET")
                .hasMessageNotContaining("QCARE_QR_SECRET");
        assertThatThrownBy(() -> ProductionSecretsCheck.check("production",
                PRIVATE_JWT, DEV_QR_SECRET, PRIVATE_SEED))
                .hasMessageContaining("QCARE_QR_SECRET");
        assertThatThrownBy(() -> ProductionSecretsCheck.check("production",
                PRIVATE_JWT, PRIVATE_QR, DEV_SEED_PASSWORD))
                .hasMessageContaining("QCARE_SEED_PASSWORD");
    }

    @Test
    void productionNamesEveryDefaultStillInUse() {
        assertThatThrownBy(() -> ProductionSecretsCheck.check(" Production ",
                DEV_JWT_SECRET, DEV_QR_SECRET, DEV_SEED_PASSWORD))
                .hasMessageContaining("QCARE_JWT_SECRET, QCARE_QR_SECRET, QCARE_SEED_PASSWORD");
    }

    @Test
    void productionWithPrivateValuesStarts() {
        assertThatCode(() -> ProductionSecretsCheck.check("production",
                PRIVATE_JWT, PRIVATE_QR, PRIVATE_SEED)).doesNotThrowAnyException();
    }

    @Test
    void outsideProductionTheDefaultsOnlyWarn() {
        for (String environment : new String[] { "", null, "development", "staging" }) {
            assertThatCode(() -> ProductionSecretsCheck.check(environment,
                    DEV_JWT_SECRET, DEV_QR_SECRET, DEV_SEED_PASSWORD)).doesNotThrowAnyException();
        }
    }

    /** The check compares against copies of the defaults; they must not drift apart. */
    @Test
    void theCheckedDefaultsAreTheOnesInApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertThat(properties.getProperty("qcare.jwt.secret"))
                .isEqualTo("${QCARE_JWT_SECRET:" + DEV_JWT_SECRET + "}");
        assertThat(properties.getProperty("qcare.qr.secret"))
                .isEqualTo("${QCARE_QR_SECRET:" + DEV_QR_SECRET + "}");
        assertThat(properties.getProperty("qcare.seed.password"))
                .isEqualTo("${QCARE_SEED_PASSWORD:" + DEV_SEED_PASSWORD + "}");
    }

    /** The whole application, not just the method: it must refuse to start. */
    @Test
    void theApplicationRefusesToStartInProductionWithADefaultSecret() {
        Throwable failure = catchThrowable(() -> {
            // Command-line arguments, because they outrank the test
            // application.properties; builder properties() would lose to it.
            ConfigurableApplicationContext context = new SpringApplicationBuilder(DemoApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(
                            "--QCARE_ENV=production",
                            "--qcare.jwt.secret=" + DEV_JWT_SECRET,
                            "--qcare.seed.password=" + PRIVATE_SEED,
                            // Its own database: starting up creates tables, and
                            // the shared one belongs to the other test classes.
                            "--spring.datasource.url=jdbc:h2:mem:secrets-check;DB_CLOSE_DELAY=-1;MODE=MySQL");
            context.close();
        });

        assertThat(failure).isNotNull();
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        // Only the JWT secret was left on its default, so only it is named.
        assertThat(cause)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QCARE_JWT_SECRET")
                .hasMessageNotContaining("QCARE_SEED_PASSWORD");
    }
}
