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

package zone.gryphon.screech;

import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AsyncHttpScreechClient implements Client, Closeable {

    private static AsyncHttpClient buildAndConfigureClient() {
        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout((int) Duration.ofSeconds(15).toMillis())
                .setFollowRedirect(true);

        return org.asynchttpclient.Dsl.asyncHttpClient(builder);
    }

    private final AsyncHttpClient asyncHttpClient;

    public AsyncHttpScreechClient() {
        this(buildAndConfigureClient());
    }

    public AsyncHttpScreechClient(AsyncHttpClient asyncHttpClient) {
        this.asyncHttpClient = Objects.requireNonNull(asyncHttpClient, "asyncHttpClient");
    }

    @Override
    public void request(SerializedRequest request, ClientCallback callback) {
        convert(request).execute(new AsyncHandler<Object>() {

            private volatile int status = -1;

            private volatile ContentCallback contentCallback;

            @Override
            public State onStatusReceived(HttpResponseStatus responseStatus) {
                status = responseStatus.getStatusCode();
                return State.CONTINUE;
            }

            @Override
            public State onHeadersReceived(HttpHeaders headers) {
                List<HttpParam> h = stream(headers.iteratorAsString())
                        .map(this::convert)
                        .collect(Collectors.toList());

                ResponseHeaders responseHeaders = ResponseHeaders.builder()
                        .status(status)
                        .headers(h)
                        .build();

                contentCallback = callback.headers(responseHeaders);
                return State.CONTINUE;
            }

            @Override
            public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
                contentCallback.content(bodyPart.getBodyByteBuffer());
                return State.CONTINUE;
            }

            @Override
            public void onThrowable(Throwable t) {
                callback.abort(t);
            }

            @Override
            public Object onCompleted() {
                callback.complete();
                return null;
            }

            private HttpParam convert(Map.Entry<String, String> entry) {
                return new HttpParam(entry.getKey(), entry.getValue());
            }

            private <T> Stream<T> stream(Iterator<T> iterator) {
                return StreamSupport.stream(((Iterable<T>) (() -> iterator)).spliterator(), false);
            }
        });
    }

    private BoundRequestBuilder convert(SerializedRequest request) {
        BoundRequestBuilder boundRequestBuilder = asyncHttpClient.prepare(request.getMethod(), request.getUri().toString());

        if (request.getHeaders() != null) {
            request.getHeaders().forEach(header -> boundRequestBuilder.addHeader(header.getKey(), header.getValue()));
        }

        if (request.getQueryParams() != null) {
            request.getQueryParams().forEach(queryParam -> boundRequestBuilder.addQueryParam(queryParam.getKey(), queryParam.getValue()));
        }

        if (request.getRequestBody() != null) {
            boundRequestBuilder.setBody(request.getRequestBody().getBody());
            boundRequestBuilder.setHeader("Content-Type", request.getRequestBody().getContentType());
        }

        return boundRequestBuilder;
    }

    @Override
    public void close() throws IOException {
        asyncHttpClient.close();
    }

    @Override
    public String toString() {
        return "AsyncHttpScreechClient{AsyncHttpClient@" + asyncHttpClient.hashCode() + '}';
    }
}
