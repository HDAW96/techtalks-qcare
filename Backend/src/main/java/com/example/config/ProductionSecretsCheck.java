package com.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stops a production server from running on the development secrets.
 *
 * application.properties gives the JWT key, the QR key and the seed password
 * development defaults, so a laptop runs with no setup. Those defaults are in
 * the repository, so anyone can read them: a production server still using one
 * would let anyone forge a sign-in token or a clinic QR code, or sign in as
 * the seeded staff.
 *
 * With QCARE_ENV=production, any default still in use stops the application
 * from starting, naming the variable to set. Anywhere else it logs one
 * warning and carries on, so local development needs no extra steps.
 *
 * Runs as the bean is created, during startup: before the web server takes a
 * request and before DataSeeder could create accounts with the default
 * password.
 */
@Component
public class ProductionSecretsCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretsCheck.class);

    /*
     * Must match the defaults in src/main/resources/application.properties.
     * ProductionSecretsCheckTest reads that file and fails if they drift apart.
     */
    static final String DEV_JWT_SECRET = "dev-only-secret-change-me-in-production-0123456789";
    static final String DEV_QR_SECRET = "dev-only-qr-secret-change-me-0123456789";
    static final String DEV_SEED_PASSWORD = "changeme123";

    public ProductionSecretsCheck(
            @Value("${QCARE_ENV:}") String environment,
            @Value("${qcare.jwt.secret}") String jwtSecret,
            @Value("${qcare.qr.secret}") String qrSecret,
            @Value("${qcare.seed.password}") String seedPassword) {
        check(environment, jwtSecret, qrSecret, seedPassword);
    }

    /**
     * @throws IllegalStateException in production, when any development default is still in use
     */
    static void check(String environment, String jwtSecret, String qrSecret, String seedPassword) {
        List<String> stillDefault = defaultsInUse(jwtSecret, qrSecret, seedPassword);
        if (stillDefault.isEmpty()) {
            return;
        }
        String variables = String.join(", ", stillDefault);
        if (isProduction(environment)) {
            throw new IllegalStateException("QCARE_ENV is production, but these settings still have "
                    + "their public development values. Set each of these environment variables to a "
                    + "private value and start again: " + variables);
        }
        log.warn("Using development defaults for {}. Fine on a laptop; set them before deploying "
                + "(with QCARE_ENV=production the server refuses to start without them).", variables);
    }

    /** The environment variables still to set, in a fixed order. */
    static List<String> defaultsInUse(String jwtSecret, String qrSecret, String seedPassword) {
        List<String> stillDefault = new ArrayList<>();
        if (DEV_JWT_SECRET.equals(jwtSecret)) {
            stillDefault.add("QCARE_JWT_SECRET");
        }
        if (DEV_QR_SECRET.equals(qrSecret)) {
            stillDefault.add("QCARE_QR_SECRET");
        }
        if (DEV_SEED_PASSWORD.equals(seedPassword)) {
            stillDefault.add("QCARE_SEED_PASSWORD");
        }
        return stillDefault;
    }

    private static boolean isProduction(String environment) {
        return environment != null && environment.trim().equalsIgnoreCase("production");
    }
}
