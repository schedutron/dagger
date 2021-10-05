package io.odpf.dagger.core.processors;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.jayway.jsonpath.InvalidJsonException;
import io.odpf.dagger.common.configuration.UserConfiguration;
import io.odpf.dagger.core.processors.telemetry.processor.MetricsTelemetryExporter;
import io.odpf.dagger.core.processors.types.Preprocessor;
import io.odpf.dagger.core.utils.Constants;

import java.util.Collections;
import java.util.List;

/**
 * The factory class for Preprocessor.
 */
public class PreProcessorFactory {
    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    /**
     * Parse config preprocessor config.
     *
     * @param userConfiguration the configuration
     * @return the preprocessor config
     */
    public static PreProcessorConfig parseConfig(UserConfiguration userConfiguration) {
        if (!userConfiguration.getParam().getBoolean(Constants.PROCESSOR_PREPROCESSOR_ENABLE_KEY, Constants.PROCESSOR_PREPROCESSOR_ENABLE_DEFAULT)) {
            return null;
        }
        String configJson = userConfiguration.getParam().get(Constants.PROCESSOR_PREPROCESSOR_CONFIG_KEY, "");
        PreProcessorConfig config;
        try {
            config = GSON.fromJson(configJson, PreProcessorConfig.class);
        } catch (JsonSyntaxException exception) {
            throw new InvalidJsonException("Invalid JSON Given for " + Constants.PROCESSOR_PREPROCESSOR_CONFIG_KEY);
        }
        return config;
    }

    /**
     * Gets preprocessors.
     *
     * @param userConfiguration        the configuration
     * @param processorConfig          the processor config
     * @param tableName                the table name
     * @param metricsTelemetryExporter the metrics telemetry exporter
     * @return the preprocessors
     */
    public static List<Preprocessor> getPreProcessors(UserConfiguration userConfiguration, PreProcessorConfig processorConfig, String tableName, MetricsTelemetryExporter metricsTelemetryExporter) {
        return Collections.singletonList(new PreProcessorOrchestrator(userConfiguration, processorConfig, metricsTelemetryExporter, tableName));
    }
}
