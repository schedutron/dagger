package io.odpf.dagger.core.source;

import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
import org.apache.flink.streaming.runtime.tasks.ExceptionInChainedOperatorException;
import org.apache.flink.types.Row;

import io.odpf.dagger.common.configuration.UserConfiguration;
import io.odpf.dagger.core.metrics.reporters.ErrorReporter;
import io.odpf.dagger.core.metrics.reporters.ErrorReporterFactory;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * A class responsible for consuming the messages in kafka.
 * {@link FlinkKafkaConsumer}.
 */
public class FlinkKafkaConsumerCustom extends FlinkKafkaConsumer<Row> {

    private ErrorReporter errorReporter;
    private UserConfiguration userConfiguration;

    /**
     * Instantiates a new Flink kafka consumer custom.
     *
     * @param subscriptionPattern the subscription pattern
     * @param deserializer        the deserializer
     * @param props               the props
     * @param userConfiguration   the configuration
     */
    public FlinkKafkaConsumerCustom(Pattern subscriptionPattern, KafkaDeserializationSchema<Row> deserializer,
                                    Properties props, UserConfiguration userConfiguration) {
        super(subscriptionPattern, deserializer, props);
        this.userConfiguration = userConfiguration;
    }

    @Override
    public void run(SourceContext<Row> sourceContext) throws Exception {
        try {
            runBaseConsumer(sourceContext);
        } catch (ExceptionInChainedOperatorException chainedOperatorException) {
            throw chainedOperatorException;
        } catch (Exception exception) {
            errorReporter = getErrorReporter(getRuntimeContext());
            errorReporter.reportFatalException(exception);
            throw exception;
        }
    }

    /**
     * Run base consumer.
     *
     * @param sourceContext the source context
     * @throws Exception the exception
     */
    protected void runBaseConsumer(SourceContext<Row> sourceContext) throws Exception {
        super.run(sourceContext);
    }

    /**
     * Gets error reporter.
     *
     * @param runtimeContext the runtime context
     * @return the error reporter
     */
    protected ErrorReporter getErrorReporter(RuntimeContext runtimeContext) {
        return ErrorReporterFactory.getErrorReporter(runtimeContext, userConfiguration);
    }
}
