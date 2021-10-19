package io.odpf.dagger.core.sink;

import org.apache.flink.api.common.functions.Function;
import org.apache.flink.api.java.utils.ParameterTool;

import io.odpf.dagger.common.configuration.UserConfiguration;
import io.odpf.dagger.common.core.StencilClientOrchestrator;
import io.odpf.dagger.core.sink.influx.InfluxRowSink;
import io.odpf.dagger.core.sink.log.LogSink;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static io.odpf.dagger.common.core.Constants.*;
import static io.odpf.dagger.core.utils.Constants.SINK_KAFKA_BROKERS_KEY;
import static io.odpf.dagger.core.utils.Constants.SINK_KAFKA_PRODUCE_LARGE_MESSAGE_ENABLE_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class SinkOrchestratorTest {

    private ParameterTool defaultParam;

    private StencilClientOrchestrator stencilClientOrchestrator;
    private SinkOrchestrator sinkOrchestrator;
    private UserConfiguration userConfiguration;

    @Before
    public void setup() {
        initMocks(this);
        defaultParam = mock(ParameterTool.class, withSettings().serializable());
        this.userConfiguration = new UserConfiguration(defaultParam);
        when(defaultParam.get(SCHEMA_REGISTRY_STENCIL_REFRESH_CACHE_KEY, SCHEMA_REGISTRY_STENCIL_REFRESH_CACHE_DEFAULT)).thenReturn(SCHEMA_REGISTRY_STENCIL_REFRESH_CACHE_DEFAULT);
        when(defaultParam.getBoolean(SCHEMA_REGISTRY_STENCIL_ENABLE_KEY, SCHEMA_REGISTRY_STENCIL_ENABLE_DEFAULT)).thenReturn(SCHEMA_REGISTRY_STENCIL_ENABLE_DEFAULT);
        when(defaultParam.get(SCHEMA_REGISTRY_STENCIL_URLS_KEY, SCHEMA_REGISTRY_STENCIL_URLS_DEFAULT)).thenReturn(SCHEMA_REGISTRY_STENCIL_URLS_DEFAULT);

        stencilClientOrchestrator = new StencilClientOrchestrator(userConfiguration);
        sinkOrchestrator = new SinkOrchestrator();
    }

    @Test
    public void shouldGiveInfluxSinkWhenConfiguredToUseInflux() throws Exception {
        defaultParam = mock(ParameterTool.class, withSettings().serializable());
        when(defaultParam.get(eq("SINK_TYPE"), anyString())).thenReturn("influx");
        userConfiguration = new UserConfiguration(defaultParam);
        Function sinkFunction = sinkOrchestrator.getSink(userConfiguration, new String[]{}, stencilClientOrchestrator);

        assertThat(sinkFunction, instanceOf(InfluxRowSink.class));
    }

    @Test
    public void shouldGiveLogSinkWhenConfiguredToUseLog() throws Exception {
        when(defaultParam.get(eq("SINK_TYPE"), anyString())).thenReturn("log");
        userConfiguration = new UserConfiguration(defaultParam);
        Function sinkFunction = sinkOrchestrator.getSink(userConfiguration, new String[]{}, stencilClientOrchestrator);

        assertThat(sinkFunction, instanceOf(LogSink.class));
    }

    @Test
    public void shouldGiveInfluxWhenConfiguredToUseNothing() throws Exception {
        when(defaultParam.get(eq("SINK_TYPE"), anyString())).thenReturn("");
        userConfiguration = new UserConfiguration(defaultParam);
        Function sinkFunction = sinkOrchestrator.getSink(userConfiguration, new String[]{}, stencilClientOrchestrator);

        assertThat(sinkFunction, instanceOf(InfluxRowSink.class));
    }


    @Test
    public void shouldSetKafkaProducerConfigurations() throws Exception {
        when(defaultParam.get(eq(SINK_KAFKA_BROKERS_KEY), anyString())).thenReturn("10.200.216.87:6668");
        when(defaultParam.getBoolean(eq(SINK_KAFKA_PRODUCE_LARGE_MESSAGE_ENABLE_KEY), anyBoolean())).thenReturn(true);
        userConfiguration = new UserConfiguration(defaultParam);
        Properties producerProperties = sinkOrchestrator.getProducerProperties(userConfiguration);

        assertEquals(producerProperties.getProperty("compression.type"), "snappy");
        assertEquals(producerProperties.getProperty("max.request.size"), "20971520");
    }

    @Test
    public void shouldGiveKafkaProducerWhenConfiguredToUseKafkaSink() throws Exception {
        when(defaultParam.get(eq("SINK_TYPE"), anyString())).thenReturn("kafka");
        when(defaultParam.get(eq("SINK_KAFKA_PROTO_MESSAGE"), anyString())).thenReturn("output_proto");
        when(defaultParam.get(eq("SINK_KAFKA_BROKERS"), anyString())).thenReturn("output_broker:2667");
        when(defaultParam.get(eq("SINK_KAFKA_TOPIC"), anyString())).thenReturn("output_topic");
        userConfiguration = new UserConfiguration(defaultParam);

        Function sinkFunction = sinkOrchestrator.getSink(userConfiguration, new String[]{}, stencilClientOrchestrator);

        assertThat(sinkFunction, instanceOf(FlinkKafkaProducerCustom.class));
    }

    @Test
    public void shouldReturnSinkMetrics() {
        ArrayList<String> sinkType = new ArrayList<>();
        sinkType.add("influx");
        HashMap<String, List<String>> expectedMetrics = new HashMap<>();
        expectedMetrics.put("sink_type", sinkType);

        when(defaultParam.get(eq("SINK_TYPE"), anyString())).thenReturn("influx");
        userConfiguration = new UserConfiguration(defaultParam);
        sinkOrchestrator.getSink(userConfiguration, new String[]{}, stencilClientOrchestrator);
        assertEquals(expectedMetrics, sinkOrchestrator.getTelemetry());
    }


    @Test
    public void shouldReturnOutputKafkaMetrics() {
        ArrayList<String> sinkType = new ArrayList<>();
        sinkType.add("kafka");
        ArrayList<String> outputTopic = new ArrayList<>();
        outputTopic.add("test_topic");
        ArrayList<String> outputStream = new ArrayList<>();
        outputStream.add("test_output_stream");
        ArrayList<String> outputProto = new ArrayList<>();
        outputProto.add("test_output_proto");

        HashMap<String, List<String>> expectedMetrics = new HashMap<>();
        expectedMetrics.put("sink_type", sinkType);
        expectedMetrics.put("output_topic", outputTopic);
        expectedMetrics.put("output_proto", outputProto);
        expectedMetrics.put("output_stream", outputStream);

        when(defaultParam.get(eq("SINK_TYPE"), anyString())).thenReturn("kafka");
        when(defaultParam.get(eq("SINK_KAFKA_PROTO_MESSAGE"), any())).thenReturn("test_output_proto");
        when(defaultParam.get(eq("SINK_KAFKA_BROKERS"), anyString())).thenReturn("output_broker:2667");
        when(defaultParam.get(eq("SINK_KAFKA_STREAM"), anyString())).thenReturn("test_output_stream");
        when(defaultParam.get(eq("SINK_KAFKA_TOPIC"), anyString())).thenReturn("test_topic");

        userConfiguration = new UserConfiguration(defaultParam);
        sinkOrchestrator.getSink(userConfiguration, new String[]{}, stencilClientOrchestrator);
        assertEquals(expectedMetrics, sinkOrchestrator.getTelemetry());
    }
}