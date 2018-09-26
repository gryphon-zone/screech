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
import org.junit.Test;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Slf4j
public class InstanceBuilderTest {

    public interface TestInterface {

        @Header("Content-Type: application/json")
        @RequestLine("GET /target?foo={foo}&bar={foo}&baz={foo}")
        CompletableFuture<String> asyncCompletableFuture(@Param("foo") String foo, String body);

        @RequestLine("GET /target?foo={param}")
        Future<String> asyncFuture(@Param("param") String foo);

        @RequestLine("GET /target")
        String sync(@Param("foo") String foo);

    }

    private static class MockClient implements Client {

        @Override
        public void request(SerializedRequest request, ClientCallback callback) {
            log.info("request: {}", request);

            if (request.getRequestBody() != null) {
                log.info("request body: {}", new String(request.getRequestBody().getBody().array()));
            } else {
                log.info("No request body");
            }

            ContentCallback contentCallback = callback.onHeaders(ResponseHeaders.builder().build());

            // call with individual bytes to test multiple invocations of onContent
            for (byte b : "Hello world!".getBytes(StandardCharsets.UTF_8)) {
                contentCallback.onContent(ByteBuffer.wrap(new byte[]{b}));
            }

            callback.complete();
        }
    }

    @Test
    public void name() throws Exception {
        TestInterface test = new InstanceBuilder(new MockClient()).build(TestInterface.class, new HardCodedTarget("http://localhost"));

        log.info("Result of sync method: {}", test.sync("foobar"));

        log.info("Result of asyncCompletableFuture method: {}", test.asyncCompletableFuture("foobar", "asdfasdfasdfasdf").get());

        log.info("Result of asyncFuture method: {}", test.asyncFuture("foobar").get());
    }
}