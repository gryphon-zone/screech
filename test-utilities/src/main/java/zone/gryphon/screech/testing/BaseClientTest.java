/*
 * Copyright 2018-2018 Gryphon Zone
 *
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
 *
 */

package zone.gryphon.screech.testing;

import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import zone.gryphon.screech.Client;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public abstract class BaseClientTest {

    @Rule
    public MockWebServer server = new MockWebServer();

    protected abstract Client createClient();

    private static String toString(ByteBuffer buffer) {
        byte[] copy = new byte[buffer.remaining()];
        buffer.duplicate().get(copy);
        return new String(copy, UTF_8);
    }

    private Client client;

    @Before
    public void setUp() {
        client = Objects.requireNonNull(createClient(), "Failed to create client");
        log.info("Testing client {}", client);
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void sanityTest() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody("this is the expected body").setResponseCode(200));

        client.request(request("GET", "/foo/bar"), callbackForSuccessfulTextRequest(future));

        try {
            assertThat(future.get()).isEqualTo("this is the expected body");
        } catch (ExecutionException e) {
            throw e.getCause();
        }

        assertThat(server.takeRequest().getPath()).isEqualTo("/foo/bar");
    }

    @Test
    public void testSimpleRedirect() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setResponseCode(307).addHeader("Location", "/bar/baz"));
        server.enqueue(new MockResponse().setBody("this is the expected body").setResponseCode(200));

        client.request(request("GET", "/foo/bar"), callbackForSuccessfulTextRequest(future));

        try {
            assertThat(future.get()).isEqualTo("this is the expected body");
        } catch (ExecutionException e) {
            throw e.getCause();
        }

        verifyRequest("GET", "/foo/bar", null, null);
        verifyRequest("GET", "/bar/baz", null, null);
    }

    @Test
    public void testPOSTRedirect() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setResponseCode(307).addHeader("Location", "/bar/baz"));
        server.enqueue(new MockResponse().setBody("this is the expected body").setResponseCode(200));

        client.request(request("POST", "/foo/bar"), callbackForSuccessfulTextRequest(future));

        try {
            assertThat(future.get()).isEqualTo("this is the expected body");
        } catch (ExecutionException e) {
            throw e.getCause();
        }

        verifyRequest("POST", "/foo/bar", null, null);
        verifyRequest("POST", "/bar/baz", null, null);
    }

    @Test
    public void testPUTRedirect() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setResponseCode(307).addHeader("Location", "/bar/baz"));
        server.enqueue(new MockResponse().setBody("this is the expected body").setResponseCode(200));

        client.request(request("PUT", "/foo/bar"), callbackForSuccessfulTextRequest(future));

        try {
            assertThat(future.get()).isEqualTo("this is the expected body");
        } catch (ExecutionException e) {
            throw e.getCause();
        }

        verifyRequest("PUT", "/foo/bar", null, null);
        verifyRequest("PUT", "/bar/baz", null, null);
    }

    @Test
    public void testRedirectWithQueryParams() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setResponseCode(307).addHeader("Location", "/bar/baz?baz=bibbly"));
        server.enqueue(new MockResponse().setBody("this is the expected body").setResponseCode(200));

        client.request(request("PUT", "/foo/bar", Collections.singletonMap("foo", "bar")), callbackForSuccessfulTextRequest(future));

        try {
            assertThat(future.get()).isEqualTo("this is the expected body");
        } catch (ExecutionException e) {
            throw e.getCause();
        }

        verifyRequest("PUT", "/foo/bar", null, Collections.singletonMap("foo", "bar"));
        verifyRequest("PUT", "/bar/baz", null, Collections.singletonMap("baz", "bibbly"));
    }

    private void verifyRequest(String method, String path, String body, Map<String, String> params) throws Exception {
        RecordedRequest request = server.takeRequest();

        if (method != null) {
            assertThat(request.getMethod()).isEqualTo(method);
        }

        if (path != null) {
            assertThat(request.getRequestUrl().encodedPath()).isEqualTo(path);
        }

        if (body != null) {
            assertThat(new String(request.getBody().readByteArray(), StandardCharsets.UTF_8)).isEqualTo(body);
        }

        if (params != null) {
            params.forEach((key, value) -> {
                assertThat(request.getRequestUrl().queryParameter(key)).isEqualTo(value);
            });
        }
    }

    private SerializedRequest request(String method, String path) {
        return request(method, path, Collections.emptyList());
    }

    private SerializedRequest request(String method, String path, Map<String, String> queryParams) {
        return request(method, path, queryParams.entrySet().stream()
                .map(e -> new HttpParam(e.getKey(), e.getValue()))
                .collect(Collectors.toList()));
    }

    private SerializedRequest request(String method, String path, List<HttpParam> queryParams) {
        return SerializedRequest.builder()
                .queryParams(queryParams)
                .method(method)
                .uri(URI.create("http://127.0.0.1:" + server.getPort() + path))
                .build();
    }


    private Client.ClientCallback callbackForSuccessfulTextRequest(CompletableFuture<String> future) {

        final StringBuffer builder = new StringBuffer();

        // since client is async, calls should never happen on the original thread
        final long originalThreadId = Thread.currentThread().getId();

        return new Client.ClientCallback() {

            private volatile boolean terminalOperationCalled = false;

            @Override
            public Client.ContentCallback headers(ResponseHeaders responseHeaders) {
                try {
                    assertThat(Thread.currentThread().getId()).isNotEqualTo(originalThreadId);
                    assertThat(terminalOperationCalled).isEqualTo(false);
                } catch (Error e) {
                    future.completeExceptionally(e);
                    throw e;
                }

                return content -> builder.append(BaseClientTest.toString(content));
            }

            @Override
            public void abort(Throwable t) {
                try {
                    assertThat(Thread.currentThread().getId()).isNotEqualTo(originalThreadId);
                    assertThat(terminalOperationCalled).isEqualTo(false);
                } catch (Error e) {
                    future.completeExceptionally(e);
                    return;
                }

                terminalOperationCalled = true;
                future.completeExceptionally(t);
            }

            @Override
            public void complete() {

                try {
                    assertThat(Thread.currentThread().getId()).isNotEqualTo(originalThreadId);
                    assertThat(terminalOperationCalled).isEqualTo(false);
                } catch (Error e) {
                    future.completeExceptionally(e);
                    return;
                }

                terminalOperationCalled = true;
                future.complete(builder.toString());
            }
        };
    }


}
