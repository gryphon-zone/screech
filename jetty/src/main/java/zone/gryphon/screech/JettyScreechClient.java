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
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseBody;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class JettyScreechClient implements Client, Closeable {

    private static HttpClient createAndConfigureClient() {
        HttpClient client = new HttpClient();
        client.setMaxConnectionsPerDestination(Short.MAX_VALUE);
        client.setMaxRequestsQueuedPerDestination(Short.MAX_VALUE);
        return client;
    }

    private final HttpClient client;

    public JettyScreechClient() {
        this(createAndConfigureClient());
    }

    public JettyScreechClient(HttpClient client) {
        this.client = client;

        try {
            this.client.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start jetty client", e);
        }
    }

    @Override
    public void request(SerializedRequest request, Client.ClientCallback callback) {
        convert(request).send(new Response.Listener.Adapter() {

            private volatile ContentCallback contentCallback;

            @Override
            public void onHeaders(Response response) {
                contentCallback = callback.onHeaders(convert(response));
            }

            @Override
            public void onContent(Response response, ByteBuffer content) {

                if (contentCallback == null) {
                    log.error("onContent() called before onHeaders()");
                    return;
                }

                contentCallback.onContent(content);
            }

            @Override
            public void onComplete(Result result) {

                if (result.getFailure() != null) {
                    callback.abort(result.getFailure());
                    return;
                }

                callback.complete();
            }
        });
    }

    private ResponseHeaders convert(Response response) {
        List<HttpParam> headers = response.getHeaders().stream()
                .map(header -> new HttpParam(header.getName(), header.getValue()))
                .collect(Collectors.toList());

        return ResponseHeaders.builder()
                .status(response.getStatus())
                .headers(headers)
                .build();
    }

    private org.eclipse.jetty.client.api.Request convert(SerializedRequest request) {
        org.eclipse.jetty.client.api.Request jettyRequest = client.newRequest(request.getUri())
                .method(request.getMethod());

        if (request.getHeaders() != null) {
//            request.getHeaders().forEach((key, values) -> values.forEach(value -> jettyRequest.header(key, value)));
        }

        if (request.getQueryParams() != null) {
//            request.getQueryParams().forEach((key, values) -> values.forEach(value -> jettyRequest.param(key, value)));
        }

        if (request.getRequestBody() != null) {
            jettyRequest.content(new ByteBufferContentProvider(request.getRequestBody().getContentType(), request.getRequestBody().getBody()));
        }

        return jettyRequest;
    }

    @Override
    public void close() throws IOException {
        try {
            this.client.stop();
        } catch (Exception e) {
            throw new IOException("Failed to close client", e);
        }
    }


}
