package com.camillus.clinic.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentConfigurationValidatorTest {
    @Test
    void earlyEnvironmentValidatorIsRegisteredWithSpringBoot() {
        assertTrue(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class,
                getClass().getClassLoader()).contains(DeploymentEnvironmentPostProcessor.class.getName()));
    }

    @Test
    void rejectsMissingOrBlankDatabaseSettingsWithoutEchoingValues() {
        MockEnvironment environment = validLocalEnvironment();
        environment.getPropertySources().remove("mockProperties");
        environment.setActiveProfiles("local");

        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> DeploymentConfigurationValidator.validate(environment));
        assertTrue(missing.getMessage().contains("DB_URL"));
        assertTrue(!missing.getMessage().contains("password"));

        MockEnvironment blankPassword = validLocalEnvironment().withProperty("DB_PASSWORD", "   ");
        IllegalStateException blank = assertThrows(IllegalStateException.class,
                () -> DeploymentConfigurationValidator.validate(blankPassword));
        assertTrue(blank.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void allowsInsecureSessionCookieOnlyInLocalProfile() {
        MockEnvironment local = validLocalEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "false");
        local.setActiveProfiles("local");
        assertDoesNotThrow(() -> DeploymentConfigurationValidator.validate(local));

        MockEnvironment unprofiled = validLocalEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "false");
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> DeploymentConfigurationValidator.validate(unprofiled));
        assertTrue(rejected.getMessage().contains("local profile"));
    }

    @Test
    void productionRequiresSecureSessionCookieAndDefaultsToSecure() {
        MockEnvironment production = validLocalEnvironment();
        production.setActiveProfiles("production");
        assertDoesNotThrow(() -> DeploymentConfigurationValidator.validate(production));

        MockEnvironment insecureProduction = validLocalEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "false");
        insecureProduction.setActiveProfiles("production");
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> DeploymentConfigurationValidator.validate(insecureProduction));
        assertTrue(rejected.getMessage().contains("SESSION_COOKIE_SECURE=true"));
    }

    @Test
    void nonStagingProfilesRejectDatabaseUrlsThatDoNotTargetCamillus() {
        for (String wrongUrl : new String[]{
                "jdbc:mysql://localhost:3306/clinic_management_db",
                "jdbc:mysql://[invalid-host/camillus",
                "jdbc:postgresql://localhost:5432/camillus"
        }) {
            MockEnvironment environment = validLocalEnvironment().withProperty("DB_URL", wrongUrl);
            environment.setActiveProfiles("local");

            IllegalStateException rejected = assertThrows(IllegalStateException.class,
                    () -> DeploymentConfigurationValidator.validate(environment));
            assertTrue(!rejected.getMessage().contains(wrongUrl));
        }
    }

    @Test
    void stagingRequiresDedicatedSettingsAndDoesNotFallBackToLiveSettings() {
        String[][] missingSettingCases = {
                {"STAGING_DB_URL", "STAGING_DB_USERNAME", "STAGING_DB_PASSWORD"},
                {"STAGING_DB_USERNAME", "STAGING_DB_URL", "STAGING_DB_PASSWORD"},
                {"STAGING_DB_PASSWORD", "STAGING_DB_URL", "STAGING_DB_USERNAME"}
        };

        for (String[] testCase : missingSettingCases) {
            MockEnvironment staging = new MockEnvironment()
                    .withProperty("DB_URL", "jdbc:mysql://localhost:3306/camillus")
                    .withProperty("DB_USERNAME", "live-user")
                    .withProperty("DB_PASSWORD", "unused")
                    .withProperty(testCase[1], testCase[1].equals("STAGING_DB_URL")
                            ? "jdbc:mysql://localhost:3306/camillus_staging" : "staging-user")
                    .withProperty(testCase[2], testCase[2].equals("STAGING_DB_PASSWORD")
                            ? "test-only-secret" : "staging-user");
            staging.setActiveProfiles("staging");

            IllegalStateException missing = assertThrows(IllegalStateException.class,
                    () -> DeploymentConfigurationValidator.validate(staging));
            assertTrue(missing.getMessage().contains(testCase[0]));
            assertTrue(!missing.getMessage().contains("unused"));
        }
    }

    @Test
    void stagingRejectsSettingsWhenStagingProfileIsNotActive() {
        MockEnvironment environment = validStagingEnvironment();
        environment.setActiveProfiles("local");

        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> DeploymentConfigurationValidator.validate(environment));
        assertTrue(rejected.getMessage().contains("require the staging profile"));
    }

    @Test
    void stagingRejectsLiveSchemaWithoutEchoingConnectionDetails() {
        MockEnvironment environment = validStagingEnvironment()
                .withProperty("STAGING_DB_URL", "jdbc:mysql://db.internal:3306/camillus")
                .withProperty("STAGING_DB_PASSWORD", "secret-for-test");
        environment.setActiveProfiles("staging");

        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> DeploymentConfigurationValidator.validate(environment));
        assertTrue(rejected.getMessage().contains("cannot target the live camillus database"));
        assertTrue(!rejected.getMessage().contains("db.internal"));
        assertTrue(!rejected.getMessage().contains("secret-for-test"));
    }

    @Test
    void stagingRejectsMalformedUrlsAndNonMysqlSchemesWithoutEchoingValues() {
        for (String invalidUrl : new String[]{
                "jdbc:mysql://[invalid-host/camillus_staging",
                "jdbc:postgresql://localhost:5432/camillus_staging",
                "jdbc:mysql://user:private@localhost:3306/camillus_staging",
                "jdbc:mysql://localhost:3306/camillus_staging?password=private"
        }) {
            MockEnvironment environment = validStagingEnvironment()
                    .withProperty("STAGING_DB_URL", invalidUrl);
            environment.setActiveProfiles("staging");

            IllegalStateException rejected = assertThrows(IllegalStateException.class,
                    () -> DeploymentConfigurationValidator.validate(environment));
            assertTrue(!rejected.getMessage().contains("private"));
            assertTrue(!rejected.getMessage().contains("localhost"));
        }
    }

    @Test
    void acceptsOnlyExplicitStagingDatabaseConfiguration() {
        MockEnvironment environment = validStagingEnvironment();
        environment.setActiveProfiles("staging");

        assertDoesNotThrow(() -> DeploymentConfigurationValidator.validate(environment));
    }

    @Test
    void stagingRejectsOtherSchemasAndConflictingProfiles() {
        for (String schema : new String[]{"other_db", "CAMILLUS_STAGING"}) {
            MockEnvironment wrongSchema = validStagingEnvironment()
                    .withProperty("STAGING_DB_URL", "jdbc:mysql://localhost:3306/" + schema);
            wrongSchema.setActiveProfiles("staging");
            assertThrows(IllegalStateException.class,
                    () -> DeploymentConfigurationValidator.validate(wrongSchema));
        }

        MockEnvironment conflictingProfiles = validStagingEnvironment();
        conflictingProfiles.setActiveProfiles("staging", "production");
        assertThrows(IllegalStateException.class,
                () -> DeploymentConfigurationValidator.validate(conflictingProfiles));
    }

    private MockEnvironment validLocalEnvironment() {
        return new MockEnvironment()
                .withProperty("DB_URL", "jdbc:mysql://localhost:3306/camillus")
                .withProperty("DB_USERNAME", "local-user")
                .withProperty("DB_PASSWORD", "test-only-secret");
    }

    private MockEnvironment validStagingEnvironment() {
        return new MockEnvironment()
                .withProperty("STAGING_DB_URL", "jdbc:mysql://localhost:3306/camillus_staging")
                .withProperty("STAGING_DB_USERNAME", "staging-user")
                .withProperty("STAGING_DB_PASSWORD", "test-only-secret");
    }
}
