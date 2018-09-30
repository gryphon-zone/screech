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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.RequestBody;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class JettyScreechClient implements Client, Closeable {

    private static class PassThroughResponseAdapter extends Response.Listener.Adapter {

        private final Client.ClientCallback callback;

        private volatile ContentCallback contentCallback;

        private PassThroughResponseAdapter(ClientCallback callback) {
            this.callback = Objects.requireNonNull(callback, "client callback may not be null");
        }

        @Override
        public void onHeaders(Response response) {
            contentCallback = callback.onHeaders(toScreechResponse(response));
        }

        @Override
        public void onContent(Response response, ByteBuffer content) {

            if (contentCallback == null) {
                log.error("content() called before onHeaders()"); // TODO replace slf4j
                return;
            }

            contentCallback.onContent(content);
        }

        @Override
        public void onComplete(Result result) {
            if (result.getFailure() != null) {
                callback.abort(result.getFailure());
            } else {
                callback.complete();
            }
        }
    }

    private static ResponseHeaders toScreechResponse(Response response) {
        List<HttpParam> headers = response.getHeaders().stream()
                .map(header -> new HttpParam(header.getName(), header.getValue()))
                .collect(Collectors.toList());

        return ResponseHeaders.builder()
                .status(response.getStatus())
                .headers(headers)
                .build();
    }

    private static HttpClient createAndConfigureClient() {
        HttpClient client = new HttpClient();
        client.setMaxConnectionsPerDestination(Short.MAX_VALUE);
        client.setMaxRequestsQueuedPerDestination(Short.MAX_VALUE);
        return client;
    }

    ////  End of statics  ////

    private final HttpClient client;

    public JettyScreechClient() {
        this(createAndConfigureClient());
    }

    public JettyScreechClient(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client may not be null");

        try {
            this.client.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start jetty client", e);
        }
    }

    @Override
    public void request(SerializedRequest request, Client.ClientCallback callback) {
        toJettyRequest(request).send(new PassThroughResponseAdapter(callback));
    }

    @Override
    public void close() throws IOException {
        try {
            this.client.stop();
        } catch (Exception e) {
            throw new IOException("Failed to close client", e);
        }
    }

    private Request toJettyRequest(SerializedRequest request) {
        Objects.requireNonNull(request, "SerializedRequest may not be null");

        Request jettyRequest = client.newRequest(request.getUri())
                .method(request.getMethod());

        if (request.getHeaders() != null) {
            for (HttpParam header : request.getHeaders()) {
                jettyRequest.header(header.getKey(), header.getValue());
            }
        }

        if (request.getQueryParams() != null) {
            for (HttpParam queryParam : request.getQueryParams()) {
                jettyRequest.param(queryParam.getKey(), queryParam.getValue());
            }
        }

        if (request.getRequestBody() != null) {
            RequestBody requestBody = request.getRequestBody();
            jettyRequest.content(new ByteBufferContentProvider(requestBody.getContentType(), requestBody.getBody()));
        }

        return jettyRequest;
    }

}
