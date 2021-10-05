package io.odpf.dagger.core.processors.longbow;

import com.google.gson.Gson;
import io.odpf.dagger.common.configuration.UserConfiguration;
import io.odpf.dagger.common.core.StencilClientOrchestrator;
import io.odpf.dagger.core.processors.longbow.columnmodifier.LongbowReadColumnModifier;
import io.odpf.dagger.core.processors.longbow.columnmodifier.LongbowWriteColumnModifier;
import io.odpf.dagger.core.processors.longbow.columnmodifier.NoOpColumnModifier;
import io.odpf.dagger.core.processors.longbow.data.LongbowProtoData;
import io.odpf.dagger.core.processors.longbow.data.LongbowTableData;
import io.odpf.dagger.core.processors.longbow.outputRow.OutputIdentity;
import io.odpf.dagger.core.processors.longbow.outputRow.OutputSynchronizer;
import io.odpf.dagger.core.processors.longbow.outputRow.ReaderOutputLongbowData;
import io.odpf.dagger.core.processors.longbow.outputRow.ReaderOutputProtoData;
import io.odpf.dagger.core.processors.longbow.processor.LongbowReader;
import io.odpf.dagger.core.processors.longbow.processor.LongbowWriter;
import io.odpf.dagger.core.processors.longbow.range.LongbowRange;
import io.odpf.dagger.core.processors.longbow.range.LongbowRangeFactory;
import io.odpf.dagger.core.processors.longbow.request.PutRequestFactory;
import io.odpf.dagger.core.processors.longbow.request.ScanRequestFactory;
import io.odpf.dagger.core.processors.longbow.validator.LongbowType;
import io.odpf.dagger.core.processors.longbow.validator.LongbowValidator;
import io.odpf.dagger.core.processors.telemetry.processor.MetricsTelemetryExporter;
import io.odpf.dagger.core.processors.types.PostProcessor;
import io.odpf.dagger.core.sink.ProtoSerializer;

import io.odpf.dagger.common.configuration.UserConfiguration;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.Map;

import static io.odpf.dagger.common.core.Constants.INPUT_STREAMS;
import static io.odpf.dagger.common.core.Constants.STREAM_INPUT_SCHEMA_PROTO_CLASS;
import static io.odpf.dagger.core.utils.Constants.*;

/**
 * The factory class for Longbow.
 */
public class LongbowFactory {
    private LongbowSchema longbowSchema;
    private UserConfiguration userConfiguration;
    private AsyncProcessor asyncProcessor;
    //    private UserConfiguration userConfiguration;
    private StencilClientOrchestrator stencilClientOrchestrator;
    private MetricsTelemetryExporter metricsTelemetryExporter;
    private String[] columnNames;
    private static final Gson GSON = new Gson();

    /**
     * Instantiates a new Longbow factory.
     *
     * @param longbowSchema             the longbow schema
     * @param userConfiguration         the userConfiguration
     * @param stencilClientOrchestrator the stencil client orchestrator
     * @param metricsTelemetryExporter  the metrics telemetry exporter
     */
    public LongbowFactory(LongbowSchema longbowSchema, UserConfiguration userConfiguration, StencilClientOrchestrator stencilClientOrchestrator, MetricsTelemetryExporter metricsTelemetryExporter) {
        this.longbowSchema = longbowSchema;
        this.userConfiguration = userConfiguration;
        this.stencilClientOrchestrator = stencilClientOrchestrator;
        this.metricsTelemetryExporter = metricsTelemetryExporter;
        this.columnNames = longbowSchema.getColumnNames().toArray(new String[0]);
        this.asyncProcessor = new AsyncProcessor();
    }

    /**
     * Instantiates a new Longbow factory.
     *
     * @param longbowSchema             the longbow schema
     * @param userConfiguration         the configuration
     * @param stencilClientOrchestrator the stencil client orchestrator
     * @param metricsTelemetryExporter  the metrics telemetry exporter
     * @param asyncProcessor            the async processor
     */
    public LongbowFactory(LongbowSchema longbowSchema, UserConfiguration userConfiguration, StencilClientOrchestrator stencilClientOrchestrator, MetricsTelemetryExporter metricsTelemetryExporter, AsyncProcessor asyncProcessor) {
        this(longbowSchema, userConfiguration, stencilClientOrchestrator, metricsTelemetryExporter);
        this.longbowSchema = longbowSchema;
        this.userConfiguration = userConfiguration;
        this.stencilClientOrchestrator = stencilClientOrchestrator;
        this.metricsTelemetryExporter = metricsTelemetryExporter;
        this.columnNames = longbowSchema.getColumnNames().toArray(new String[0]);
        this.asyncProcessor = asyncProcessor;
    }

    /**
     * Gets longbow processor.
     *
     * @return the longbow processor
     */
    public PostProcessor getLongbowProcessor() {
        LongbowReader longbowReader;
        LongbowWriter longbowWriter;
        LongbowValidator longbowValidator = new LongbowValidator(columnNames);
        LongbowType longbowType = longbowSchema.getType();

        ArrayList<RichAsyncFunction<Row, Row>> longbowRichFunctions = new ArrayList<>();
        longbowValidator.validateLongbow(longbowType);
        switch (longbowType) {
            case LongbowWrite:
                longbowWriter = longbowWriterPlus();
                longbowRichFunctions.add(longbowWriter);
                longbowWriter.notifySubscriber(metricsTelemetryExporter);
                return new LongbowProcessor(asyncProcessor, userConfiguration, longbowRichFunctions, new LongbowWriteColumnModifier());
            case LongbowRead:
                longbowReader = longbowReaderPlus();
                longbowRichFunctions.add(longbowReader);
                longbowReader.notifySubscriber(metricsTelemetryExporter);
                return new LongbowProcessor(asyncProcessor, userConfiguration, longbowRichFunctions, new LongbowReadColumnModifier());
            default:
                longbowWriter = longbowWriter();
                longbowReader = longbowReader();
                longbowRichFunctions.add(longbowWriter);
                longbowRichFunctions.add(longbowReader);
                longbowWriter.notifySubscriber(metricsTelemetryExporter);
                longbowReader.notifySubscriber(metricsTelemetryExporter);
                return new LongbowProcessor(asyncProcessor, userConfiguration, longbowRichFunctions, new NoOpColumnModifier());
        }
    }

    private LongbowReader longbowReaderPlus() {
        LongbowRange longbowRange = LongbowRangeFactory.getLongbowRange(longbowSchema);
        ScanRequestFactory scanRequestFactory = new ScanRequestFactory(longbowSchema, getTableId(userConfiguration));
        ReaderOutputProtoData readerOutputRow = new ReaderOutputProtoData(longbowSchema);
        LongbowProtoData longbowTableData = new LongbowProtoData();
        return new LongbowReader(userConfiguration, longbowSchema, longbowRange, longbowTableData, scanRequestFactory, readerOutputRow);
    }

    private LongbowReader longbowReader() {
        LongbowRange longbowRange = LongbowRangeFactory.getLongbowRange(longbowSchema);
        ScanRequestFactory scanRequestFactory = new ScanRequestFactory(longbowSchema, getTableId(userConfiguration));
        ReaderOutputLongbowData readerOutputRow = new ReaderOutputLongbowData(longbowSchema);
        LongbowTableData longbowTableData = new LongbowTableData(longbowSchema);
        return new LongbowReader(userConfiguration, longbowSchema, longbowRange, longbowTableData, scanRequestFactory, readerOutputRow);
    }

    private LongbowWriter longbowWriterPlus() {
        ProtoSerializer protoSerializer = new ProtoSerializer(null, getMessageProtoClassName(userConfiguration), columnNames, stencilClientOrchestrator);
        String tableId = getTableId(userConfiguration);
        PutRequestFactory putRequestFactory = new PutRequestFactory(longbowSchema, protoSerializer, tableId);
        OutputSynchronizer outputSynchronizer = new OutputSynchronizer(longbowSchema, tableId, getMessageProtoClassName(userConfiguration));
        return new LongbowWriter(userConfiguration, longbowSchema, putRequestFactory, tableId, outputSynchronizer);
    }

    private LongbowWriter longbowWriter() {
        String tableId = getTableId(userConfiguration);
        PutRequestFactory putRequestFactory = new PutRequestFactory(longbowSchema, null, tableId);
        OutputIdentity outputIdentity = new OutputIdentity();
        return new LongbowWriter(userConfiguration, longbowSchema, putRequestFactory, tableId, outputIdentity);
    }

    private String getTableId(UserConfiguration userConfiguration) {
        return userConfiguration
                .getParam()
                .get(PROCESSOR_LONGBOW_GCP_TABLE_ID_KEY, userConfiguration.getParam().get(DAGGER_NAME_KEY, DAGGER_NAME_DEFAULT));
    }

    private String getMessageProtoClassName(UserConfiguration userConfiguration) {
        String jsonArrayString = userConfiguration.getParam().get(INPUT_STREAMS, "");
        Map[] streamsConfig = GSON.fromJson(jsonArrayString, Map[].class);
        return (String) streamsConfig[0].get(STREAM_INPUT_SCHEMA_PROTO_CLASS);
    }
}
