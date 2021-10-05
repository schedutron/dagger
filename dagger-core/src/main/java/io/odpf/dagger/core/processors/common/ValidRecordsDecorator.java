package io.odpf.dagger.core.processors.common;

import io.odpf.dagger.core.metrics.reporters.ErrorReporter;
import io.odpf.dagger.core.metrics.reporters.ErrorReporterFactory;
import io.odpf.dagger.core.processors.types.FilterDecorator;
import com.google.protobuf.InvalidProtocolBufferException;
import io.odpf.dagger.core.utils.Constants;

import org.apache.flink.api.common.functions.RichFilterFunction;

import io.odpf.dagger.common.configuration.UserConfiguration;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.types.Row;

import java.util.Arrays;

/**
 * The Valid records decorator.
 */
public class ValidRecordsDecorator extends RichFilterFunction<Row> implements FilterDecorator {

    private final String tableName;
    private final int validationIndex;
    private final UserConfiguration userConfiguration;
    /**
     * The Error reporter.
     */
    protected ErrorReporter errorReporter;

    /**
     * Instantiates a new Valid records decorator.
     *
     * @param tableName         the table name
     * @param columns           the columns
     * @param userConfiguration
     */
    public ValidRecordsDecorator(String tableName, String[] columns, UserConfiguration userConfiguration) {
        this.tableName = tableName;
        validationIndex = Arrays.asList(columns).indexOf(Constants.INTERNAL_VALIDATION_FILED_KEY);
        this.userConfiguration = userConfiguration;
    }

    @Override
    public void open(Configuration configuration) throws Exception {
        errorReporter = ErrorReporterFactory.getErrorReporter(getRuntimeContext(), userConfiguration);
    }

    @Override
    public Boolean canDecorate() {
        return true;
    }

    @Override
    public boolean filter(Row value) throws Exception {
        if (!(boolean) value.getField(validationIndex)) {
            Exception ex = new InvalidProtocolBufferException("Bad Record Encountered for table `" + this.tableName + "`");
            errorReporter.reportFatalException(ex);
            throw ex;
        }
        return true;
    }
}
