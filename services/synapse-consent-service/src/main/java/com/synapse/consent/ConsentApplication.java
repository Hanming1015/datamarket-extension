package com.synapse.consent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Consent service entry point: consent-rule CRUD + field-level matching engine.
 * Split out of the monolith's {@code consentmanagement} package + {@code ConsentMatchingEngine}.
 */
@SpringBootApplication
public class ConsentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsentApplication.class, args);
    }
}
