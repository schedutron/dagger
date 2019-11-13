package com.gojek.daggers.postProcessors.external.es;

import com.gojek.daggers.metrics.StatsManager;
import com.gojek.daggers.postProcessors.common.ColumnNameManager;
import com.gojek.daggers.postProcessors.common.RowMaker;
import com.gojek.daggers.postProcessors.external.common.RowManager;
import com.gojek.esb.driverprofile.DriverProfileLogMessage;
import com.google.protobuf.Descriptors;
import mockit.Mock;
import mockit.MockUp;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.types.Row;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.ParseException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class EsResponseHandlerTest {

    private ResultFuture resultFuture;
    private Descriptors.Descriptor descriptor;
    private StatsManager statsManager;
    private Row streamData;
    private EsResponseHandler esResponseHandler;
    private Response response;
    private EsSourceConfig esSourceConfig;
    private Row inputData;
    private Row outputData;
    private RowManager rowManager;
    private ColumnNameManager columnNameManager;
    private String[] inputColumnNames;
    private ArrayList<String> outputColumnNames;

    @Before
    public void setUp() {
        streamData = new Row(2);
        inputData = new Row(3);
        outputData = new Row(4);
        streamData.setField(0,inputData);
        streamData.setField(1,outputData);
        rowManager = new RowManager(streamData);
        esSourceConfig = new EsSourceConfig("localhost", "9200", "",
                "driver_id", "com.gojek.esb.fraud.DriverProfileFlattenLogMessage", "30",
                "5000", "5000", "5000", "5000", false, new HashMap<>());
        resultFuture = mock(ResultFuture.class);
        descriptor = DriverProfileLogMessage.getDescriptor();
        statsManager = mock(StatsManager.class);
        inputColumnNames = new String[3];
        outputColumnNames = new ArrayList<>();
        columnNameManager = new ColumnNameManager(inputColumnNames, outputColumnNames);
        esResponseHandler = new EsResponseHandler(esSourceConfig,statsManager,rowManager, columnNameManager,descriptor,resultFuture);
        response = mock(Response.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        HttpEntity httpEntity = mock(HttpEntity.class);
        when(response.getEntity()).thenReturn(httpEntity);
    }

    @Test
    public void shouldCompleteResultFutureWithInput() {
        MockUp<EntityUtils> mockUp = new MockUp<EntityUtils>() {
            @Mock
            public String toString(HttpEntity entity) {
                return "{\"_source\": {\"driver_id\":\"12345\"}}";
            }
        };

        esResponseHandler.startTimer();
        esResponseHandler.onSuccess(response);

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));

        mockUp.tearDown();
    }

    @Test
    public void shouldHandleParseExceptionAndReturnInput() {
        MockUp<EntityUtils> mockUp = new MockUp<EntityUtils>() {
            @Mock
            public String toString(HttpEntity entity) {
                throw new ParseException("Parsing failed!!!!");
            }
        };

        esResponseHandler.startTimer();
        esResponseHandler.onSuccess(response);

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));

        mockUp.tearDown();
    }

    @Test
    public void shouldHandleIOExceptionAndReturnInput() {
        MockUp<EntityUtils> mockUp = new MockUp<EntityUtils>() {
            @Mock
            public String toString(HttpEntity entity) throws IOException {
                throw new IOException("IO failed!!!!");
            }
        };

        esResponseHandler.startTimer();
        esResponseHandler.onSuccess(response);

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));

        mockUp.tearDown();
    }

    @Test
    public void shouldHandleExceptionAndReturnInput() {

        MockUp<EntityUtils> mockUp = new MockUp<EntityUtils>() {
            @Mock
            public String toString(HttpEntity entity) throws IOException {
                throw new NullPointerException("Null!!!");
            }
        };

        esResponseHandler.startTimer();
        esResponseHandler.onSuccess(response);

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));

        mockUp.tearDown();
    }

    @Test
    public void shouldHandleResponseParsingIOExceptionAndReturnInput() {
        MockUp<EntityUtils> mockUpEntityUtils = new MockUp<EntityUtils>() {
            @Mock
            public String toString(HttpEntity entity) {
                return "{\"_source\": {\"driver_id\":\"12345\"}}";
            }
        };

        MockUp<RowMaker> mockUpRowMaker = new MockUp<RowMaker>() {
            @Mock
            public Row makeRow(Map<String, Object> inputMap, Descriptors.Descriptor descriptor) throws IOException {
                throw new IOException("RowMaker failed");
            }
        };

        esResponseHandler.startTimer();
        esResponseHandler.onSuccess(response);

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));

        mockUpEntityUtils.tearDown();
        mockUpRowMaker.tearDown();
    }

    @Test
    public void shouldHandleOnFailure() throws IOException {
        Response response = mock(Response.class);
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getMethod()).thenReturn("GET");
        when(response.getRequestLine()).thenReturn(requestLine);
        HttpHost httpHost = new HttpHost("test", 9091, "test");
        when(response.getHost()).thenReturn(httpHost);
        when(requestLine.getUri()).thenReturn("/drivers/driver/11223344545");
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.toString()).thenReturn("Test");
        when(response.getStatusLine()).thenReturn(statusLine);

        esResponseHandler.startTimer();
        esResponseHandler.onFailure(new ResponseException(response));

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));
    }

    @Test
    public void shouldHandleForNotFoundOnFailure() throws IOException {
        Response response = mock(Response.class);
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getMethod()).thenReturn("GET");
        when(response.getRequestLine()).thenReturn(requestLine);
        HttpHost httpHost = new HttpHost("test", 9091, "test");
        when(response.getHost()).thenReturn(httpHost);
        when(requestLine.getUri()).thenReturn("/drivers/driver/11223344545");
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.toString()).thenReturn("Test");
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(404);

        esResponseHandler.startTimer();
        esResponseHandler.onFailure(new ResponseException(response));

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));
    }

    @Test
    public void shouldHandleForRetryStatusOnFailure() throws IOException {
        Response response = mock(Response.class);
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getMethod()).thenReturn("GET");
        when(response.getRequestLine()).thenReturn(requestLine);
        HttpHost httpHost = new HttpHost("test", 9091, "test");
        when(response.getHost()).thenReturn(httpHost);
        when(requestLine.getUri()).thenReturn("/drivers/driver/11223344545");
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.toString()).thenReturn("Test");
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(502);

        esResponseHandler.startTimer();
        esResponseHandler.onFailure(new ResponseException(response));

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));
    }

    @Test
    public void shouldHandleForNonResponseExceptionOnFailure() throws IOException {
        Response response = mock(Response.class);
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getMethod()).thenReturn("GET");
        when(response.getRequestLine()).thenReturn(requestLine);
        HttpHost httpHost = new HttpHost("test", 9091, "test");
        when(response.getHost()).thenReturn(httpHost);
        when(requestLine.getUri()).thenReturn("/drivers/driver/11223344545");
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.toString()).thenReturn("Test");
        when(response.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(502);

        esResponseHandler.startTimer();
        esResponseHandler.onFailure(new IOException(""));

        verify(resultFuture, times(1)).complete(Collections.singleton(streamData));
    }

}