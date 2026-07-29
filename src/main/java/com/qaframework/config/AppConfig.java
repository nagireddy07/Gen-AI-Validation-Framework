package com.qaframework.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties once and exposes typed getters.
 * Keeps every "moving part" (endpoint, key, thresholds, file paths) in one place
 * so nothing is hardcoded inside the test / client classes.
 */
public final class AppConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new IllegalStateException("config.properties not found on classpath (src/main/resources).");
            }
            PROPS.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.properties: " + e.getMessage(), e);
        }
    }

    private AppConfig() {
    }

    public static String getAzureEndpoint() {
        return PROPS.getProperty("azure.endpoint");
    }

    public static String getAzureApiKey() {
        return PROPS.getProperty("azure.api.key");
    }

    public static String getAzureDeployment() {
        return PROPS.getProperty("azure.deployment");
    }

    public static String getAzureApiVersion() {
        return PROPS.getProperty("azure.api.version");
    }

    public static String getInputExcelPath() {
        return PROPS.getProperty("input.excel.path");
    }

    public static String getOutputExcelPath() {
        return PROPS.getProperty("output.excel.path");
    }

    public static int getPassThreshold() {
        return Integer.parseInt(PROPS.getProperty("pass.threshold", "75"));
    }

    public static int getMaxRetries() {
        return Integer.parseInt(PROPS.getProperty("max.retries", "5"));
    }

    public static long getBaseBackoffMs() {
        return Long.parseLong(PROPS.getProperty("base.backoff.ms", "1000"));
    }

    public static long getMaxBackoffMs() {
        return Long.parseLong(PROPS.getProperty("max.backoff.ms", "30000"));
    }
}
