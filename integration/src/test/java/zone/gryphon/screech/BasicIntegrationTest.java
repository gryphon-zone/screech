/*
 * Copyright 2019-2019 Gryphon Zone
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.bridge.SLF4JBridgeHandler;
import zone.gryphon.screech.gson2.GsonDecoderFactory;
import zone.gryphon.screech.gson2.GsonEncoder;
import zone.gryphon.screech.jackson2.JacksonDecoderFactory;
import zone.gryphon.screech.jackson2.JacksonEncoder;
import zone.gryphon.screech.util.HardCodedTarget;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(Parameterized.class)
public class BasicIntegrationTest {

    static {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
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

        @RequestLine("GET /widgets/findByName?name={name}")
        CompletableFuture<List<Widget>> findByName(@Param("name") String name);

        @RequestLine("GET /widgets/findByName?name={name}")
        List<Widget> findByNameSynchronous(@Param("name") String name);

        @RequestLine("POST /widgets/{id}")
        CompletableFuture<Optional<Widget>> getWidget(@Param("id") UUID id);

        @RequestLine("POST /widgets/{id}")
        Optional<Widget> getWidgetSynchronous(@Param("id") UUID id);

    }

    @Parameterized.Parameters
    public static List<ScreechBuilder> parameters() {
        return Arrays.asList(
                new ScreechBuilder(new AsyncHttpScreechClient())
                        .requestEncoder(new GsonEncoder())
                        .responseDecoder(new GsonDecoderFactory()),

                new ScreechBuilder(new AsyncHttpScreechClient())
                        .requestEncoder(new JacksonEncoder())
                        .responseDecoder(new JacksonDecoderFactory()),

                new ScreechBuilder(new JettyScreechClient())
                        .requestEncoder(new GsonEncoder())
                        .responseDecoder(new GsonDecoderFactory()),

                new ScreechBuilder(new JettyScreechClient())
                        .requestEncoder(new JacksonEncoder())
                        .responseDecoder(new JacksonDecoderFactory())
        );
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Rule
    public MockWebServer server = new MockWebServer();

    @Parameterized.Parameter
    public ScreechBuilder builder;

    private WidgetApi widgetApi;

    @Before
    public void setup() {
        log.info("Builder: {}", builder);
        widgetApi = builder.build(WidgetApi.class, new HardCodedTarget("http://127.0.0.1:" + server.getPort()));
    }

    @Test(timeout = 15000)
    public void testPostWithBody() throws Exception {
        Widget request = new Widget(UUID.randomUUID());
        Widget response = new Widget(UUID.randomUUID());

        server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(response)));

        Widget result = widgetApi.submitWidget(request).get();
        log.info("Result: {}", result);

        assertThat(result).isEqualTo(response);
        assertThat(body(server.takeRequest())).isEqualTo(objectMapper.writeValueAsString(request));
    }

    @Test(timeout = 15000)
    public void testPostWithBodySynchronous() throws Exception {
        Widget request = widget();
        Widget response = widget();

        server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(response)));

        Widget result = widgetApi.submitWidgetSynchronous(request);
        log.info("Result: {}", result);

        assertThat(result).isEqualTo(response);
        assertThat(body(server.takeRequest())).isEqualTo(objectMapper.writeValueAsString(request));
    }

    @Test(timeout = 15000)
    public void testFindByName() throws Exception {
        List<Widget> list = Arrays.asList(widget(), widget(), widget());

        server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(list)));

        List<Widget> result = widgetApi.findByName("foo").get();
        log.info("Result: {}", result);

        assertThat(result).isEqualTo(list);
    }

    @Test(timeout = 15000)
    public void testFindByNameSynchronous() throws Exception {
        List<Widget> list = Arrays.asList(widget(), widget(), widget());

        server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(list)));

        List<Widget> result = widgetApi.findByNameSynchronous("foo");
        log.info("Result: {}", result);

        assertThat(result).isEqualTo(list);
    }

    @Test(timeout = 15000)
    public void testGet() throws Exception {
        Widget widget = widget();

        server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(widget)));

        Optional<Widget> result = widgetApi.getWidget(widget.getUuid()).get();
        log.info("Result: {}", result);

        assertThat(result).contains(widget);
    }

    @Test(timeout = 15000)
    public void testGetSynchronous() throws Exception {
        Widget widget = widget();

        String responseBody = objectMapper.writeValueAsString(widget);

        server.enqueue(new MockResponse().setBody(responseBody));

        Optional<Widget> result = widgetApi.getWidgetSynchronous(widget.getUuid());
        log.info("Result: {}", result);

        assertThat(result).contains(widget);
    }

    private String body(RecordedRequest recordedRequest) {
        return new String(recordedRequest.getBody().readByteArray(), UTF_8);
    }

    private Widget widget() {
        return new Widget(UUID.randomUUID());
    }
}
