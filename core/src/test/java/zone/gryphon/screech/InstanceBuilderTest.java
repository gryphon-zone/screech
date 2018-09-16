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
import zone.gryphon.screech.model.ResponseBody;
import zone.gryphon.screech.model.SerializedRequest;
import zone.gryphon.screech.model.SerializedResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Slf4j
public class InstanceBuilderTest {

    public interface TestInterface {

        @Header("Content-Type: application/json")
        @RequestLine("GET /target?foo={foo}&bar={foo}&baz={foo}")
        CompletableFuture<String> async(@Param("foo") String foo, String body);

        @RequestLine("GET /target?foo={param}")
        Future<String> asyncFuture(@Param("foo") String foo);

        @RequestLine("GET /target")
        String sync(@Param("foo") String foo);

    }

    private static class MockClient implements Client {

        @Override
        public void request(SerializedRequest request, Callback<SerializedResponse> callback) {
            log.info("request: {}", request);
            if (request.getRequestBody() != null) {
                log.info("request body: {}", new String(request.getRequestBody().getBody().array()));
            } else {
                log.info("No request body");
            }
            callback.onSuccess(SerializedResponse.builder().responseBody(ResponseBody.builder().buffer(ByteBuffer.wrap("Hello world!".getBytes())).build()).build());
        }
    }

    @Test
    public void name() throws Exception {

        TestInterface test =  new InstanceBuilder(new MockClient()).build(TestInterface.class, new HardCodedTarget("http://localhost"));

        log.info("Result of sync method: {}", test.sync("foobar"));

        log.info("Result of async method: {}", test.async("foobar", "asdfasdfasdfasdf").get());

        log.info("Result of async future method: {}", test.asyncFuture("foobar").get());
    }
}