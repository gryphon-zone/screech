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

package zone.gryphon.screech.internal;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.Client;
import zone.gryphon.screech.RequestEncoder;
import zone.gryphon.screech.RequestInterceptor;
import zone.gryphon.screech.RequestLine;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.ScreechBuilder;
import zone.gryphon.screech.Target;
import zone.gryphon.screech.model.Request;
import zone.gryphon.screech.model.Response;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;
import zone.gryphon.screech.util.ExpandableByteBuffer;
import zone.gryphon.screech.util.HardCodedTarget;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

@SuppressWarnings("CodeBlock2Expr")
@Slf4j
public class AsyncInvocationHandlerComponentDelaysTest {

    private static final String THREADPOOL_NAME_PREFIX = "test-thread-" + UUID.randomUUID().toString();

    private static final ScheduledExecutorService THREADPOOL = Executors.newScheduledThreadPool(5 * Runtime.getRuntime().availableProcessors(), new ThreadFactory() {

        private final AtomicInteger threadId = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, THREADPOOL_NAME_PREFIX + "-" + threadId.getAndIncrement());
        }
    });

    private static final long DELAY = 250L;

    private static class TestException extends RuntimeException {
        public TestException(@NonNull String id) {
            super("This is a mock exception for a unit test. id=" + id);
        }
    }

    public interface TestApi {

        @SuppressWarnings("unused")
        @RequestLine("GET /foo")
        CompletableFuture<String> eventuallyResult(String body);

    }

    private static class MockClient implements Client {

        private final int status;

        private MockClient(int status) {
            this.status = status;
        }

        @Override
        public void request(SerializedRequest request, ClientCallback callback) {
            ensureNotRunningInTestThreadPool();

            String body = new String(request.getRequestBody().getBody().array(), UTF_8);

            ResponseHeaders headers = ResponseHeaders.builder()
                    .status(status)
                    .headers(Collections.emptyList())
                    .build();

            ContentCallback contentCallback = callback.headers(headers);

            contentCallback.content(ByteBuffer.wrap(String.format("response: %s", body).getBytes(UTF_8)));

            callback.complete();
        }
    }

    private static class MockRequestEncoder implements RequestEncoder {

        @Override
        public <T> void encode(T entity, Callback<ByteBuffer> callback) {

            ensureNotRunningInTestThreadPool();

            if (!String.class.equals(entity.getClass())) {
                throw new IllegalArgumentException("Only works with strings! got " + entity.getClass().getName());
            }

            callback.onSuccess(ByteBuffer.wrap(((String) entity).getBytes(UTF_8)));
        }
    }

    private static class MockResponseDecoder implements ResponseDecoder {

        private final Callback<Object> callback;

        private final boolean isError;

        private final ExpandableByteBuffer buffer = ExpandableByteBuffer.createEmpty();

        private MockResponseDecoder(Callback<Object> callback, boolean isError) {
            this.callback = callback;
            this.isError = isError;
        }

        @Override
        public void content(ByteBuffer content) {
            buffer.append(content);
        }

        @Override
        public void complete() {
            if (isError) {
                callback.onFailure(new RuntimeException(AsyncInvocationHandlerComponentDelaysTest.toString(buffer.createInputStream())));
            } else {
                callback.onSuccess(AsyncInvocationHandlerComponentDelaysTest.toString(buffer.createInputStream()));
            }
        }
    }

    private static class MockResponseDecoderFactory implements ResponseDecoderFactory {

        @Override
        public ResponseDecoder create(ResponseHeaders response, Type type, Callback<Object> callback) {
            return new MockResponseDecoder(callback, false);
        }
    }

    private static class MockErrorResponseDecoderFactory implements ResponseDecoderFactory {

        @Override
        public ResponseDecoder create(ResponseHeaders response, Type type, Callback<Object> callback) {
            return new MockResponseDecoder(callback, true);
        }
    }


    @Builder
    private static class TestApiBuilder {

        @Builder.Default
        private Target target = new HardCodedTarget("http://localhost:8080");

        @Builder.Default
        private Client client = new MockClient(200);

        @Builder.Default
        private RequestEncoder requestEncoder = new MockRequestEncoder();

        @Builder.Default
        private ResponseDecoderFactory responseDecoderFactory = new MockResponseDecoderFactory();

        @Builder.Default
        private ResponseDecoderFactory errorDecoderFactory = new MockErrorResponseDecoderFactory();

        @Builder.Default
        private List<RequestInterceptor> requestInterceptors = new ArrayList<>();

        public TestApi build() {
            return new ScreechBuilder(client)
                    .requestEncoder(requestEncoder)
                    .responseDecoder(responseDecoderFactory)
                    .errorDecoder(errorDecoderFactory)
                    .addRequestInterceptors(requestInterceptors)
                    .build(TestApi.class, target);
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

            return result.toString(UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read string", e);
        }
    }

    private static <T> T unwrap(Future<T> future) throws ExecutionException {
        try {
            return future.get(1, TimeUnit.MINUTES);
        } catch (TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensureNotRunningInTestThreadPool() {
        log.info("Running in thread \"{}\"", Thread.currentThread().getName());

        assertThat(Thread.currentThread().getName()).doesNotContain(THREADPOOL_NAME_PREFIX);
    }

    private static <T> CompletableFuture<T> ensureNotCompletedUsingTestThreadPool(CompletableFuture<T> future) {
        return future.handle((result, throwable) -> {

            ensureNotRunningInTestThreadPool();

            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }

            if (throwable instanceof Error) {
                throw (Error) throwable;
            }

            if (throwable != null) {
                throw new RuntimeException(throwable);
            }

            return result;
        });
    }

    private static void testHappyPathHelper(Function<String, TestApiBuilder.TestApiBuilderBuilder> supplier) {
        String id = String.valueOf(UUID.randomUUID());

        // build the API first so that initialization time doesn't affect result
        TestApi api = supplier.apply(id).build().build();

        long startTime = System.nanoTime();

        // submit request, this should be non-blocking
        CompletableFuture<String> future = ensureNotCompletedUsingTestThreadPool(api.eventuallyResult(id));

        // since submitting request should be non-blocking, it should have taken less than 250ms to submit the request
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)).isLessThan(DELAY);

        try {
            // get the result and make sure it's the correct one
            assertThat(unwrap(future)).isEqualTo("response: " + id);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof Error) {
                throw (Error) cause;
            }

            throw new RuntimeException(cause);
        }

        // since one component of the client should delay for 250ms, getting the final result should take more than that
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)).isGreaterThanOrEqualTo(DELAY);
    }

    private static void testFailurePathHelper(Function<String, TestApiBuilder.TestApiBuilderBuilder> supplier) {
        String id = String.valueOf(UUID.randomUUID());

        // build the API first so that initialization time doesn't affect result
        TestApi api = supplier.apply(id).build().build();

        long startTime = System.nanoTime();

        // submit request, this should be non-blocking
        CompletableFuture<String> future = ensureNotCompletedUsingTestThreadPool(api.eventuallyResult(id));

        // since submitting request should be non-blocking, it should have taken less than 250ms to submit the request
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)).isLessThan(DELAY);

        // get the result and make sure it's the correct one
        try {
            unwrap(future);
            failBecauseExceptionWasNotThrown(TestException.class);
        } catch (ExecutionException e) {

            // since one component of the client should delay for 250ms, getting the final result should take more than that
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)).isGreaterThanOrEqualTo(DELAY);

            Throwable cause = e.getCause();

            if (cause instanceof Error) {
                throw (Error) cause;
            }

            assertThat(cause).isNotNull();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause.getMessage()).isEqualTo("This is a mock exception for a unit test. id=" + id);
        }
    }

    @Rule
    public final TestName testName = new TestName();

    private final AtomicReference<Throwable> exceptionForWhenMethodCalledThatShouldNotHaveBeenCalled = new AtomicReference<>();

    @Before
    public void setUp() {
        log.info("Running test {}", testName.getMethodName());
    }

    @After
    public void cleanup() {
        Throwable maybeThrowable = exceptionForWhenMethodCalledThatShouldNotHaveBeenCalled.get();

        if (maybeThrowable != null) {
            throw new RuntimeException("Method which should not have been called was called", maybeThrowable);
        }
    }

    @Test(timeout = 5000)
    public void testIfRequestInterceptorIsAsyncAndSucceedsAndDoesNotPropagateRequest() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            responseCallback.onSuccess(Response.builder().entity(String.format("response: %s", id)).build());
                        }, DELAY, MILLISECONDS);
                    }
                })));
    }

    @Test(timeout = 5000)
    public void testIfRequestInterceptorIsAsyncAndSucceedsAndPropagatesRequest() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.accept(request, new Callback<Response<Y>>() {
                                @Override
                                public void onSuccess(Response<Y> result) {
                                    ensureNotRunningInTestThreadPool();
                                    responseCallback.onSuccess(result);
                                }

                                @Override
                                public void onFailure(Throwable e) {
                                    ensureNotRunningInTestThreadPool();
                                }
                            });
                        }, DELAY, MILLISECONDS);
                    }
                })));
    }

    @Test(timeout = 5000)
    public void testIfRequestAndResponseInterceptorsAreAsyncAndSucceed() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.accept(request, new Callback<Response<Y>>() {
                                @Override
                                public void onSuccess(Response<Y> result) {
                                    THREADPOOL.schedule(() -> {
                                        responseCallback.onSuccess(result);
                                    }, DELAY, MILLISECONDS);
                                }

                                @Override
                                public void onFailure(Throwable e) {
                                    exceptionForWhenMethodCalledThatShouldNotHaveBeenCalled.set(new RuntimeException("onFailure() called when it should not have been", e));
                                    ensureNotRunningInTestThreadPool();
                                }
                            });
                        }, DELAY, MILLISECONDS);
                    }
                })));
    }

    @Test(timeout = 5000)
    public void testIfRequestInterceptorIsAsyncAndFails() {
        testFailurePathHelper(id -> TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            responseCallback.onFailure(new TestException(id));
                        }, DELAY, MILLISECONDS);
                    }
                })));
    }

    @Test(timeout = 5000)
    public void testIfResponseInterceptorIsAsyncAndSucceeds() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        ensureNotRunningInTestThreadPool();

                        callback.accept(request, new Callback<Response<Y>>() {
                            @Override
                            public void onSuccess(Response<Y> result) {
                                ensureNotRunningInTestThreadPool();

                                THREADPOOL.schedule(() -> {
                                    responseCallback.onSuccess(Response.builder().entity(String.format("response: %s", id)).build());
                                }, DELAY, MILLISECONDS);
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                ensureNotRunningInTestThreadPool();
                            }
                        });
                    }
                })));
    }

    @Test(timeout = 5000)
    public void testIfResponseInterceptorIsAsyncAndFails() {
        testFailurePathHelper(id -> TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        ensureNotRunningInTestThreadPool();

                        callback.accept(request, new Callback<Response<Y>>() {
                            @Override
                            public void onSuccess(Response<Y> result) {
                                ensureNotRunningInTestThreadPool();

                                THREADPOOL.schedule(() -> {
                                    responseCallback.onFailure(new TestException(id));
                                }, DELAY, MILLISECONDS);
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                ensureNotRunningInTestThreadPool();
                            }
                        });
                    }
                })));
    }


    @Test(timeout = 5000)
    public void testIfRequestEncoderIsAsyncAndSucceeds() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .requestEncoder(new RequestEncoder() {
                    @Override
                    public <T> void encode(T entity, Callback<ByteBuffer> callback) {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.onSuccess(ByteBuffer.wrap(String.valueOf(entity).getBytes(UTF_8)));
                        }, DELAY, MILLISECONDS);
                    }
                }));
    }

    @Test(timeout = 5000)
    public void testIfRequestEncoderIsAsyncAndFails() {
        testFailurePathHelper(id -> TestApiBuilder.builder()
                .requestEncoder(new RequestEncoder() {
                    @Override
                    public <T> void encode(T entity, Callback<ByteBuffer> callback) {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.onFailure(new TestException(id));
                        }, DELAY, MILLISECONDS);
                    }
                }));
    }

    @Test(timeout = 5000)
    public void testIfClientIsAsyncAndSucceeds() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .client((request, callback) -> {
                    ensureNotRunningInTestThreadPool();

                    final CompletableFuture<Client.ContentCallback> future = new CompletableFuture<>();

                    byte[] bytes = String.format("response: %s", id).getBytes(UTF_8);

                    // deliver every byte of the content asynchronously
                    CompletableFuture<Client.ContentCallback> localFuture = future;

                    for (byte b : bytes) {
                        localFuture = localFuture.thenApplyAsync(contentCallback -> {
                            contentCallback.content(ByteBuffer.wrap(new byte[]{b}));
                            return contentCallback;
                        }, THREADPOOL);
                    }

                    localFuture.thenRunAsync(callback::complete, THREADPOOL);

                    THREADPOOL.schedule(() -> {
                        future.complete(callback.headers(ResponseHeaders.builder().status(200).build()));
                    }, DELAY, MILLISECONDS);
                }));
    }

    @Test(timeout = 5000)
    public void testIfClientIsAsyncAndFails() {
        testFailurePathHelper(id -> TestApiBuilder.builder()
                .client((request, callback) -> {
                    ensureNotRunningInTestThreadPool();

                    final CompletableFuture<Client.ContentCallback> future = new CompletableFuture<>();

                    byte[] bytes = String.format("response: %s", id).getBytes(UTF_8);

                    // deliver every byte of the content asynchronously
                    CompletableFuture<Client.ContentCallback> localFuture = future;

                    for (byte b : bytes) {
                        localFuture = localFuture.thenApplyAsync(contentCallback -> {
                            contentCallback.content(ByteBuffer.wrap(new byte[]{b}));
                            return contentCallback;
                        }, THREADPOOL);
                    }

                    localFuture.thenRunAsync(() -> callback.abort(new TestException(id)), THREADPOOL);

                    THREADPOOL.schedule(() -> {
                        future.complete(callback.headers(ResponseHeaders.builder().status(200).build()));
                    }, DELAY, MILLISECONDS);
                }));
    }

    @Test(timeout = 5000)
    public void testIfResponseDecoderIsAsyncAndSucceeds() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> new ResponseDecoder() {

                    private final ExpandableByteBuffer expandableByteBuffer = ExpandableByteBuffer.createEmpty();

                    @Override
                    public void content(ByteBuffer content) {
                        ensureNotRunningInTestThreadPool();
                        expandableByteBuffer.append(content);
                    }

                    @Override
                    public void complete() {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.onSuccess(AsyncInvocationHandlerComponentDelaysTest.toString(expandableByteBuffer.createInputStream()));
                        }, DELAY, MILLISECONDS);
                    }
                }));
    }

    @Test(timeout = 5000)
    public void testIfResponseDecoderIsAsyncAndFails() {
        testFailurePathHelper(id -> TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> new ResponseDecoder() {

                    private final ExpandableByteBuffer expandableByteBuffer = ExpandableByteBuffer.createEmpty();

                    @Override
                    public void content(ByteBuffer content) {
                        ensureNotRunningInTestThreadPool();
                        expandableByteBuffer.append(content);
                    }

                    @Override
                    public void complete() {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.onFailure(new TestException(id));
                        }, DELAY, MILLISECONDS);
                    }
                }));
    }

    @Test(timeout = 5000)
    public void testIfErrorDecoderIsAsyncAndSucceeds() {
        testHappyPathHelper(id -> TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> new ResponseDecoder() {

                    private final ExpandableByteBuffer expandableByteBuffer = ExpandableByteBuffer.createEmpty();

                    @Override
                    public void content(ByteBuffer content) {
                        ensureNotRunningInTestThreadPool();
                        expandableByteBuffer.append(content);
                    }

                    @Override
                    public void complete() {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.onSuccess(AsyncInvocationHandlerComponentDelaysTest.toString(expandableByteBuffer.createInputStream()));
                        }, DELAY, MILLISECONDS);
                    }
                }));
    }

    @Test(timeout = 5000)
    public void testIfErrorDecoderIsAsyncAndFails() {
        testFailurePathHelper(id -> TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> new ResponseDecoder() {

                    private final ExpandableByteBuffer expandableByteBuffer = ExpandableByteBuffer.createEmpty();

                    @Override
                    public void content(ByteBuffer content) {
                        ensureNotRunningInTestThreadPool();
                        expandableByteBuffer.append(content);
                    }

                    @Override
                    public void complete() {
                        ensureNotRunningInTestThreadPool();

                        THREADPOOL.schedule(() -> {
                            callback.onFailure(new TestException(id));
                        }, DELAY, MILLISECONDS);
                    }
                }));
    }


}
