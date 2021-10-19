package io.odpf.dagger.core.metrics.reporters;

import org.apache.flink.api.common.functions.RuntimeContext;

import io.odpf.dagger.common.configuration.UserConfiguration;
import io.odpf.dagger.core.utils.Constants;

/**
 * The Factory class for Error reporter.
 */
public class ErrorReporterFactory {

    /**
     * Gets error reporter.
     *
     * @param runtimeContext    the runtime context
     * @param userConfiguration the configuration
     * @return the error reporter
     */
    public static ErrorReporter getErrorReporter(RuntimeContext runtimeContext, UserConfiguration userConfiguration) {
        long shutDownPeriod = userConfiguration.getParam().getLong(Constants.METRIC_TELEMETRY_SHUTDOWN_PERIOD_MS_KEY, Constants.METRIC_TELEMETRY_SHUTDOWN_PERIOD_MS_DEFAULT);
        boolean telemetryEnabled = userConfiguration.getParam().getBoolean(Constants.METRIC_TELEMETRY_ENABLE_KEY, Constants.METRIC_TELEMETRY_ENABLE_VALUE_DEFAULT);
        return getErrorReporter(runtimeContext, telemetryEnabled, shutDownPeriod);
    }

    /**
     * Gets error reporter.
     *
     * @param runtimeContext  the runtime context
     * @param telemetryEnable the telemetry enable
     * @param shutDownPeriod  the shut down period
     * @return the error reporter
     */
    public static ErrorReporter getErrorReporter(RuntimeContext runtimeContext, Boolean telemetryEnable, long shutDownPeriod) {
        if (telemetryEnable) {
            return new ErrorStatsReporter(runtimeContext, shutDownPeriod);
        } else {
            return new NoOpErrorReporter();
        }
    }
}