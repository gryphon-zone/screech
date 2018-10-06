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

import junit.framework.TestCase;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.bridge.SLF4JBridgeHandler;
import zone.gryphon.screech.Client;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.RequestBody;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;
import zone.gryphon.screech.util.ExpandableByteBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

@Slf4j
public abstract class BaseClientTest {

    static {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
    }

    private static final String[] HTTP_METHODS = {
            "GET", "PUT", "POST", "DELETE"
    };

    private static final String BODY = "this is the expected message body";

    protected abstract Client createClient();

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public final TestName testName = new TestName();

    private Client client;

    @Before
    public void setUp() {
        client = Objects.requireNonNull(createClient(), "Failed to create client");
        log.info("Running test {} using client {}", testName.getMethodName(), client);
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testSimpleGET() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        client.request(request("GET", "/foo/bar"), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        assertThat(server.takeRequest().getPath()).isEqualTo("/foo/bar");
    }

    @Test
    public void testSimpleQueryParams() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        List<HttpParam> queryParams = Arrays.asList(
                HttpParam.from("foo", "bar"),
                HttpParam.from("bar", "baz"),
                HttpParam.from("baz", "bibbly"));

        client.request(request("GET", "/foo", queryParams), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("GET", "/foo", null, queryParams, null);
    }

    @Test
    public void testDuplicatedQueryParams() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        List<HttpParam> queryParams = Arrays.asList(
                HttpParam.from("foo", "bar"),
                HttpParam.from("foo", "baz"),
                HttpParam.from("foo", "bibbly"));

        client.request(request("GET", "/foo", queryParams), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("GET", "/foo", null, queryParams, null);
    }

    @Test
    public void testEmptyQueryParam() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        List<HttpParam> queryParams = Collections.singletonList(HttpParam.from("foo", ""));

        client.request(request("GET", "/foo", queryParams), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("GET", "/foo", null, queryParams, null);
    }

    @Test
    public void testSimpleHeaderParams() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        List<HttpParam> headerParams = Arrays.asList(
                HttpParam.from("foo", "bar"),
                HttpParam.from("bar", "baz"),
                HttpParam.from("baz", "bibbly"));

        client.request(request("GET", "/foo", emptyList(), headerParams), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("GET", "/foo", null, null, headerParams);
    }

    @Test
    public void testDuplicatedHeaderParams() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        List<HttpParam> headerParams = Arrays.asList(
                HttpParam.from("foo", "bar"),
                HttpParam.from("foo", "baz"),
                HttpParam.from("foo", "bibbly"));

        client.request(request("GET", "/foo", emptyList(), headerParams), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("GET", "/foo", null, null, headerParams);
    }

    @Test
    public void testEmptyHeaderParam() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        List<HttpParam> headerParams = Collections.singletonList(HttpParam.from("foo", ""));

        client.request(request("GET", "/foo", emptyList(), headerParams), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("GET", "/foo", null, null, headerParams);
    }


    @Test
    public void testSimpleRedirects() throws Throwable {
        for (String method : HTTP_METHODS) {
            CompletableFuture<String> future = new CompletableFuture<>();

            server.enqueue(new MockResponse().setResponseCode(307).addHeader("Location", "/bar/baz"));
            server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

            client.request(request(method, "/foo/bar"), callback(future));

            assertThat(unwrap(future)).isEqualTo(BODY);

            verifyRequest(method, "/foo/bar", null, null, null);
            verifyRequest(method, "/bar/baz", null, null, null);
        }
    }

    @Test
    public void testRedirectWithQueryParams() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        server.enqueue(new MockResponse().setResponseCode(307).addHeader("Location", "/bar/baz?baz=bibbly"));
        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        client.request(request("GET", "/foo/bar", Collections.singletonList(HttpParam.from("foo", "bar"))), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("GET", "/foo/bar", null, Collections.singletonList(HttpParam.from("foo", "bar")), null);
        verifyRequest("GET", "/bar/baz", null, Collections.singletonList(HttpParam.from("baz", "bibbly")), null);
    }

    @Test
    public void testUpload() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        String uploadBody = "this is the request upload body";

        server.enqueue(new MockResponse().setBody(BODY).setResponseCode(200));

        client.request(request("POST", "/foo/bar", emptyList(), emptyList(), uploadBody), callback(future));

        assertThat(unwrap(future)).isEqualTo(BODY);

        verifyRequest("POST", "/foo/bar", uploadBody, null, null);
    }

    @Test
    public void testConnectionRefused() throws Throwable {
        CompletableFuture<String> future = new CompletableFuture<>();

        SerializedRequest request = request("GET", "/foo/bar").toBuilder()
                .uri(URI.create("http://127.0.0.1:" + (server.getPort() + 1) + "/foo/bar"))
                .build();

        client.request(request, callback(future));

        try {
            unwrap(future);
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            assertThat(e).hasRootCauseInstanceOf(ConnectException.class);
            log.debug("Caught expected exception", e.getCause());
        }
    }

    @Test
    public void testConcurrentRequests() throws Throwable {
        CompletableFuture<String> future1 = new CompletableFuture<>();
        CompletableFuture<String> future2 = new CompletableFuture<>();

        client.request(request("GET", "/request/one"), callback(future1));
        client.request(request("GET", "/request/two"), callback(future2));

        // ensure both requests are pending
        assertThat(future1).isNotCompleted();
        assertThat(future2).isNotCompleted();

        server.enqueue(new MockResponse().setResponseCode(200).setBody("response one"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("response two"));

        String responseOne = unwrap(future1);
        String responseTwo = unwrap(future2);

        assertThat(responseOne).isNotEqualTo(responseTwo);
        assertThat(responseOne).isIn("response one", "response two");
        assertThat(responseTwo).isIn("response one", "response two");

        RecordedRequest requestOne = getRequest();
        RecordedRequest requestTwo = getRequest();

        assertThat(requestOne.getPath()).isNotEqualTo(requestTwo.getPath());
        assertThat(requestOne.getPath()).isIn("/request/one", "/request/two");
        assertThat(requestTwo.getPath()).isIn("/request/one", "/request/two");
    }

    @Test
    public void testsRequestMethodsWithVariousStatusCodes() throws Throwable {
        final int[] statusCodes = {
                200,
                202,
                204,
                // 307 is tested above
                400,
                401,
                403,
                409,
                412,
                418, // very important
                429,
                500,
                502,
                503
        };

        final List<HttpParam> queryParams = Arrays.asList(
                HttpParam.from("one", "two"),
                HttpParam.from("three", "four"),
                HttpParam.from("five", "six")
        );

        final List<HttpParam> headerParams = Arrays.asList(
                HttpParam.from("X-one", "two"),
                HttpParam.from("X-three", "four"),
                HttpParam.from("X-five", "six")
        );

        final String requestBody = "request body";

        for (String method : HTTP_METHODS) {
            for (int statusCode : statusCodes) {

                CompletableFuture<String> future = new CompletableFuture<>();

                MockResponse response = new MockResponse().setResponseCode(statusCode);

                if (204 != statusCode) {
                    response.setBody(BODY);
                }

                if (401 == statusCode) {
                    response.setHeader("WWW-Authenticate", "foo");
                }

                server.enqueue(response);

                if ("GET".equals(method)) {
                    client.request(request(method, "/path", queryParams, headerParams), callback(future));
                } else {
                    client.request(request(method, "/path", queryParams, headerParams, requestBody), callback(future));
                }

                if (statusCode != 204) {
                    assertThat(unwrap(future)).isEqualTo(BODY);
                } else {
                    assertThat(unwrap(future)).isEqualTo("");
                }

                String expectedBody = "GET".equalsIgnoreCase(method) ? null : requestBody;

                verifyRequest(method, "/path", expectedBody, queryParams, headerParams);
            }
        }
    }

    private static String toString(InputStream stream) {
        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            return result.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read string", e);
        }
    }

    private <T> T unwrap(Future<T> future) throws Throwable {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error) {
                throw e.getCause();
            } else {
                throw e;
            }
        } catch (TimeoutException e) {
            TestCase.fail("Request failed to complete within 1 second");
            return null; // unreachable
        }
    }

    private RecordedRequest getRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);

        assertThat(request).withFailMessage("Expected client to make a request to server, but none found").isNotNull();

        return request;
    }

    private void verifyRequest(String method, String path, String body, List<HttpParam> queryParams, List<HttpParam> headerParams) throws Exception {
        RecordedRequest request = getRequest();

        if (method != null) {
            assertThat(request.getMethod()).isEqualTo(method);
        }

        if (path != null) {
            assertThat(request.getRequestUrl().encodedPath()).isEqualTo(path);
        }

        if (body != null) {
            assertThat(new String(request.getBody().readByteArray(), UTF_8)).isEqualTo(body);
        }

        if (queryParams != null) {
            String expectedQueryString = queryParams.stream().map(this::toQueryParam).collect(Collectors.joining("&"));
            assertThat(request.getRequestUrl().query()).isEqualTo(expectedQueryString);
        }

        if (headerParams != null) {
            assertThat(request.getHeaders().size()).isGreaterThanOrEqualTo(headerParams.size());
            headerParams.forEach(header -> assertThat(request.getHeaders().toMultimap()).containsKey(header.getKey()));
            headerParams.forEach(header -> assertThat(request.getHeaders().toMultimap().get(header.getKey())).contains(header.getValue()));
        }
    }

    private String toQueryParam(HttpParam param) {
        StringBuilder builder = new StringBuilder();

        builder.append(param.getKey());
        if (param.getValue() != null) {
            builder.append("=");
            builder.append(param.getValue());
        }

        return builder.toString();
    }

    private SerializedRequest request(String method, String path) {
        return request(method, path, Collections.emptyList());
    }

    private SerializedRequest request(String method, String path, List<HttpParam> queryParams) {
        return request(method, path, queryParams, emptyList(), null);
    }

    private SerializedRequest request(String method, String path, List<HttpParam> queryParams, List<HttpParam> headerParams) {
        return request(method, path, queryParams, headerParams, null);
    }

    private SerializedRequest request(String method, String path, List<HttpParam> queryParams, List<HttpParam> headerParams, String body) {
        SerializedRequest.SerializedRequestBuilder builder = SerializedRequest.builder()
                .queryParams(queryParams)
                .headers(headerParams)
                .method(method)
                .uri(URI.create("http://127.0.0.1:" + server.getPort() + path));

        if (body != null) {
            builder.requestBody(RequestBody.builder().contentType("octet/stream").body(ByteBuffer.wrap(body.getBytes(UTF_8))).build());
        }

        return builder.build();
    }


    private Client.ClientCallback callback(CompletableFuture<String> future) {

        // since client is async, calls should never happen on the original thread
        final long originalThreadId = Thread.currentThread().getId();

        return new Client.ClientCallback() {

            private ExpandableByteBuffer buffer;

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

                buffer = responseHeaders.getContentLength()
                        .map(ExpandableByteBuffer::create)
                        .orElseGet(ExpandableByteBuffer::createEmpty);

                return buffer::append;
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

                try (InputStream inputStream = buffer.createInputStream()) {
                    future.complete(BaseClientTest.toString(inputStream));
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            }
        };
    }
}
