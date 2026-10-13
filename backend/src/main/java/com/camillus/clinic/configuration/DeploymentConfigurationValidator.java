package com.camillus.clinic.configuration;

import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Validates deployment targets before Spring creates the DataSource or Flyway beans. */
public final class DeploymentConfigurationValidator {
    private static final String STAGING_PROFILE = "staging";
    private static final String STAGING_SCHEMA = "camillus_staging";
    private static final String LIVE_SCHEMA = "camillus";

    private DeploymentConfigurationValidator() {
    }

    public static void validate(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean stagingProfile = hasProfile(activeProfiles, STAGING_PROFILE);
        boolean testProfile = hasProfile(activeProfiles, "test");
        boolean anyStagingSetting = Arrays.stream(new String[]{
                        "STAGING_DB_URL", "STAGING_DB_USERNAME", "STAGING_DB_PASSWORD"})
                .anyMatch(name -> environment.getProperty(name) != null);

        if (stagingProfile) {
            if (hasProfile(activeProfiles, "local") || hasProfile(activeProfiles, "production") || testProfile) {
                throw new IllegalStateException("The staging profile cannot be combined with local, production, or test profiles");
            }
            requireNonBlank(environment, "STAGING_DB_URL");
            requireNonBlank(environment, "STAGING_DB_USERNAME");
            requireNonBlank(environment, "STAGING_DB_PASSWORD");
            validateStagingUrl(environment.getProperty("STAGING_DB_URL"));
            validateCookiePolicy(environment, activeProfiles);
            return;
        }

        if (anyStagingSetting) {
            throw new IllegalStateException("STAGING_DB_* settings require the staging profile");
        }
        if (testProfile) {
            return;
        }

        requireNonBlank(environment, "DB_URL");
        requireNonBlank(environment, "DB_USERNAME");
        requireNonBlank(environment, "DB_PASSWORD");
        validateMySqlUrl(environment.getProperty("DB_URL"), LIVE_SCHEMA, "DB_URL");
        validateCookiePolicy(environment, activeProfiles);
    }

    private static void validateStagingUrl(String jdbcUrl) {
        validateMySqlUrl(jdbcUrl, STAGING_SCHEMA, "STAGING_DB_URL");
    }

    private static void validateMySqlUrl(String jdbcUrl, String expectedSchema, String settingName) {
        final URI uri;
        try {
            if (!jdbcUrl.startsWith("jdbc:")) {
                throw new URISyntaxException("", "not a JDBC URL");
            }
            uri = new URI(jdbcUrl.substring("jdbc:".length()));
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(settingName + " must be a valid MySQL JDBC URL targeting " + expectedSchema);
        }

        String schema = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (!"mysql".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException(settingName + " must be a MySQL JDBC URL without embedded credentials");
        }
        rejectCredentialQueryParameters(uri.getRawQuery(), settingName);
        if (STAGING_SCHEMA.equals(expectedSchema) && schema.equalsIgnoreCase(LIVE_SCHEMA)) {
            throw new IllegalStateException("The staging profile cannot target the live camillus database");
        }
        if (!expectedSchema.equals(schema)) {
            throw new IllegalStateException(settingName + " must target the " + expectedSchema + " database");
        }
    }

    private static void rejectCredentialQueryParameters(String query, String settingName) {
        if (query == null || query.isBlank()) {
            return;
        }
        try {
            boolean containsCredential = Arrays.stream(query.split("&"))
                    .map(parameter -> parameter.split("=", 2)[0])
                    .map(key -> URLDecoder.decode(key, StandardCharsets.UTF_8))
                    .map(key -> key.toLowerCase(Locale.ROOT))
                    .anyMatch(key -> key.equals("user") || key.equals("username") || key.equals("password"));
            if (containsCredential) {
                throw new IllegalStateException(settingName + " must not contain credential parameters");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(settingName + " contains invalid query parameters");
        }
    }

    private static void validateCookiePolicy(Environment environment, String[] activeProfiles) {
        boolean localProfile = hasProfile(activeProfiles, "local");
        boolean productionProfile = hasProfile(activeProfiles, "production");
        boolean secureCookie = Boolean.parseBoolean(environment.getProperty(
                "server.servlet.session.cookie.secure", "true").toLowerCase(Locale.ROOT));

        if (productionProfile && !secureCookie) {
            throw new IllegalStateException("Production requires secure session cookies; set SESSION_COOKIE_SECURE=true");
        }
        if (!secureCookie && !localProfile) {
            throw new IllegalStateException("Insecure session cookies are allowed only with the explicit local profile");
        }
    }

    private static boolean hasProfile(String[] profiles, String expected) {
        return Arrays.stream(profiles).anyMatch(profile -> profile.equalsIgnoreCase(expected));
    }

    private static void requireNonBlank(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required database setting is missing or blank: " + name);
        }
    }
}
