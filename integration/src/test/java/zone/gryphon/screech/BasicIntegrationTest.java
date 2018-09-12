package zone.gryphon.screech;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;
import zone.gryphon.screech.jackson.JacksonDecoder;
import zone.gryphon.screech.jackson.JacksonEncoder;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class BasicIntegrationTest {

    static {
        SLF4JBridgeHandler.install();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Widget {

        private UUID uuid;

    }

    interface WidgetApi {

        @RequestLine("POST /widgets/submit")
        CompletableFuture<Widget> submitWidget(Widget widget);

        @RequestLine("POST /widgets/submit")
        Widget submitWidgetSynchronous(Widget widget);

    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Rule
    public MockWebServer server = new MockWebServer();

    private WidgetApi widgetApi;

    @Before
    public void setup() {
        widgetApi = new InstanceBuilder(new JettyScreechClient())
                .addRequestInterceptor(new RequestInterceptor.PassThroughRequestInterceptor())
                .requestEncoder(new JacksonEncoder())
                .responseDecoder(new JacksonDecoder())
                .build(WidgetApi.class, new HardCodedTarget("http://127.0.0.1:" + server.getPort()));
    }

    @Test(timeout = 15000)
    public void testPostWithBody() throws Exception {
        Widget request = new Widget(UUID.randomUUID());

        Widget response = new Widget(UUID.randomUUID());

        server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(response)));

        CompletableFuture<Widget> future = widgetApi.submitWidget(request);

        future.whenComplete((widget, error) -> {
            if (error != null) {
                log.error("Error in test", error);
            } else {
                log.info("Widget: {}", widget);
            }

            assertThat(error).isNull();
            assertThat(widget).isEqualTo(response);
        }).get();

        RecordedRequest recordedRequest = server.takeRequest();

        assertThat(body(recordedRequest)).isEqualTo(objectMapper.writeValueAsString(request));
    }

    @Test(timeout = 15000)
    public void testPostWithBodySynchronous() throws Exception {
        Widget request = new Widget(UUID.randomUUID());

        Widget response = new Widget(UUID.randomUUID());

        server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(response)));

        Widget widget = widgetApi.submitWidgetSynchronous(request);
        log.info("Widget: {}", widget);
        assertThat(widget).isEqualTo(response);

        RecordedRequest recordedRequest = server.takeRequest();
        assertThat(body(recordedRequest)).isEqualTo(objectMapper.writeValueAsString(request));
    }

    private String body(RecordedRequest recordedRequest) {
        return new String(recordedRequest.getBody().readByteArray(), UTF_8);
    }
}
