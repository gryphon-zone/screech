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

package zone.gryphon.screech.internal;


import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

/**
 * Tests to make sure that the the completable future in a client interface is always completed, even
 * if an exception is thrown by one of the modules
 */
@SuppressWarnings("unchecked")
@Slf4j
public class AsyncInvocationHandlerNoDeadlocksTest {

    private static class TestException extends RuntimeException {
        public TestException(@NonNull String id) {
            super("This is a mock exception for a unit test. id=" + id);
        }
    }

    private static class AnotherTestException extends RuntimeException {
        public AnotherTestException(@NonNull String id) {
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
            String body = new String(request.getRequestBody().getBody().array(), StandardCharsets.UTF_8);

            ResponseHeaders headers = ResponseHeaders.builder()
                    .status(status)
                    .headers(Collections.emptyList())
                    .build();

            ContentCallback contentCallback = callback.headers(headers);

            contentCallback.content(ByteBuffer.wrap(String.format("response: %s", body).getBytes(StandardCharsets.UTF_8)));

            callback.complete();
        }
    }

    private static class MockRequestEncoder implements RequestEncoder {

        @Override
        public <T> void encode(T entity, Callback<ByteBuffer> callback) {

            if (!String.class.equals(entity.getClass())) {
                throw new IllegalArgumentException("Only works with strings! got " + entity.getClass().getName());
            }

            callback.onSuccess(ByteBuffer.wrap(((String) entity).getBytes(StandardCharsets.UTF_8)));
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
                callback.onFailure(new RuntimeException(AsyncInvocationHandlerNoDeadlocksTest.toString(buffer.createInputStream())));
            } else {
                callback.onSuccess(AsyncInvocationHandlerNoDeadlocksTest.toString(buffer.createInputStream()));
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

            return result.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read string", e);
        }
    }

    private static RuntimeException createInvalidCallException() {
        return new RuntimeException("The screech method should not have been called, but it was");
    }

    private <T> void unwrap(Future<T> future) throws ExecutionException {
        try {
            future.get(1, TimeUnit.MINUTES);
        } catch (TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test(timeout = 1000)
    public void testIfTargetThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .target(() -> {
                    throw new TestException(id);
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfRequestEncoderThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .requestEncoder(new RequestEncoder() {
                    @Override
                    public <T> void encode(T entity, Callback<ByteBuffer> callback) {
                        throw new TestException(id);
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfRequestEncoderCompletesWithException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .requestEncoder(new RequestEncoder() {
                    @Override
                    public <T> void encode(T entity, Callback<ByteBuffer> callback) {
                        callback.onFailure(new TestException(id));
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfRequestInterceptorThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        throw new TestException(id);
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfRequestInterceptorCompletesWithException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        responseCallback.onFailure(new TestException(id));
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }


    @Test(timeout = 1000)
    public void testIfClientThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(((request, callback) -> {
                    throw new TestException(id);
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfClientCompletesWithException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(((request, callback) -> callback.abort(new TestException(id))))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    // response decoder

    @Test(timeout = 1000)
    public void testIfResponseDecoderFactoryThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> {
                    throw new TestException(id);
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfResponseDecoderFactoryCompletesWithException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> {
                    callback.onFailure(new TestException(id));
                    return null;
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfResponseDecoderThrowsExceptionInContentMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        throw new TestException(id);
                    }

                    @Override
                    public void complete() {
                        log.error("complete should not have been called", createInvalidCallException());
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfResponseDecoderThrowsExceptionInCompleteMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        // ignore
                    }

                    @Override
                    public void complete() {
                        throw new TestException(id);
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfResponseDecoderCompletesWithExceptionInContentMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        callback.onFailure(new TestException(id));
                    }

                    @Override
                    public void complete() {
                        log.error("complete should not have been called", createInvalidCallException());
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfResponseDecoderCompletesWithExceptionInCompleteMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .responseDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        // ignore
                    }

                    @Override
                    public void complete() {
                        callback.onFailure(new TestException(id));
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    // error response decoder

    @Test(timeout = 1000)
    public void testIfErrorResponseDecoderFactoryThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> {
                    throw new TestException(id);
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseDecoderFactoryCompletesWithException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> {
                    callback.onFailure(new TestException(id));
                    return null;
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseDecoderThrowsExceptionInContentMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        throw new TestException(id);
                    }

                    @Override
                    public void complete() {
                        log.error("complete should not have been called", createInvalidCallException());
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseDecoderThrowsExceptionInCompleteMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        // ignore
                    }

                    @Override
                    public void complete() {
                        throw new TestException(id);
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseDecoderCompletesWithExceptionInContentMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        callback.onFailure(new TestException(id));
                    }

                    @Override
                    public void complete() {
                        log.error("complete should not have been called", createInvalidCallException());
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseDecoderCompletesWithExceptionInCompleteMethod() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .errorDecoderFactory((response, type, callback) -> new ResponseDecoder() {
                    @Override
                    public void content(ByteBuffer content) {
                        // ignore
                    }

                    @Override
                    public void complete() {
                        callback.onFailure(new TestException(id));
                    }
                })
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    // response interceptor

    @Test(timeout = 1000)
    public void testIfResponseInterceptorThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                throw new TestException(id);
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                log.error("onFailure should not have been called", createInvalidCallException());
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfResponseInterceptorCompletesWithException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                responseCallback.onFailure(new TestException(id));
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                log.error("onFailure should not have been called", createInvalidCallException());
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseInterceptorThrowsException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                throw new TestException(id);
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseInterceptorCompletesWithException() {
        String id = String.valueOf(UUID.randomUUID());

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .requestInterceptors(Collections.singletonList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                responseCallback.onFailure(new TestException(id));
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

    // ensure second interceptor gets called if first fails

    @Test(timeout = 1000)
    public void testIfResponseInterceptorThrowsExceptionSecondGetsCalled() {
        String id = String.valueOf(UUID.randomUUID());
        AtomicBoolean called = new AtomicBoolean();

        TestApi test = TestApiBuilder.builder()
                .requestInterceptors(Arrays.asList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                called.set(true);
                                responseCallback.onFailure(e);
                            }
                        });
                    }
                }, new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                throw new TestException(id);
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                log.error("onFailure should not have been called", createInvalidCallException());
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
            assertThat(called).isTrue();
        }
    }

    @Test(timeout = 1000)
    public void testIfResponseInterceptorCompletesWithExceptionSecondGetsCalled() {
        String id = String.valueOf(UUID.randomUUID());
        AtomicBoolean called = new AtomicBoolean();

        TestApi test = TestApiBuilder.builder()
                .requestInterceptors(Arrays.asList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                called.set(true);
                                responseCallback.onFailure(e);
                            }
                        });
                    }
                }, new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                responseCallback.onFailure(new TestException(id));
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                log.error("onFailure should not have been called", createInvalidCallException());
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
            assertThat(called).isTrue();
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseInterceptorThrowsExceptionSecondGetsCalled() {
        String id = String.valueOf(UUID.randomUUID());
        AtomicBoolean called = new AtomicBoolean();

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .requestInterceptors(Arrays.asList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                called.set(true);
                                responseCallback.onFailure(e);
                            }
                        });
                    }
                }, new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                throw new TestException(id);
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
            assertThat(called).isTrue();
        }
    }

    @Test(timeout = 1000)
    public void testIfErrorResponseInterceptorCompletesWithExceptionSecondGetsCalled() {
        String id = String.valueOf(UUID.randomUUID());
        AtomicBoolean called = new AtomicBoolean();

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .requestInterceptors(Arrays.asList(new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                called.set(true);
                                responseCallback.onFailure(e);
                            }
                        });
                    }
                }, new RequestInterceptor() {
                    @Override
                    public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                        callback.accept(request, new Callback<Response<Y>>() {

                            @Override
                            public void onSuccess(Response<Y> result) {
                                log.error("onSuccess should not have been called", createInvalidCallException());
                            }

                            @Override
                            public void onFailure(Throwable e) {
                                responseCallback.onFailure(new TestException(id));
                            }
                        });
                    }
                }))
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
            assertThat(called).isTrue();
        }
    }

    @Test(timeout = 1000)
    public void testIfAllErrorResponseInterceptorCallbacksThrowException() {
        final int count = 20;
        String id = String.valueOf(UUID.randomUUID());

        List<RequestInterceptor> interceptors = new ArrayList<>();

        IntStream.range(0, count).mapToObj(index -> new RequestInterceptor() {
            @Override
            public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                callback.accept(request, new Callback<Response<Y>>() {

                    @Override
                    public void onSuccess(Response<Y> result) {
                        log.error("onSuccess should not have been called", createInvalidCallException());
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        throw new AnotherTestException("callback #" + index);
                    }
                });
            }
        }).forEach(interceptors::add);

        interceptors.add(new RequestInterceptor() {

            @Override
            public <X, Y> void intercept(Request<X> request, BiConsumer<Request<?>, Callback<Response<Y>>> callback, Callback<Response<?>> responseCallback) {
                callback.accept(request, new Callback<Response<Y>>() {

                    @Override
                    public void onSuccess(Response<Y> result) {
                        log.error("onSuccess should not have been called", createInvalidCallException());
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        responseCallback.onFailure(new TestException(id));
                    }
                });
            }
        });

        TestApi test = TestApiBuilder.builder()
                .client(new MockClient(500))
                .requestInterceptors(interceptors)
                .build()
                .build();

        try {
            unwrap(test.eventuallyResult(id));
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            for (int i = 0; i < count; i++) {
                assertThat(cause).isInstanceOf(AnotherTestException.class);
                assertThat(cause).hasMessage("This is a mock exception for a unit test. id=callback #" + i);
                assertThat(cause.getSuppressed()).hasSize(1);
                cause = cause.getSuppressed()[0];
            }

            assertThat(cause).isInstanceOf(TestException.class);
            assertThat(cause).hasMessageContaining(id);
        }
    }

}