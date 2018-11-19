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
import zone.gryphon.screech.util.HardCodedTarget;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class ScreechBuilderTest {

    @Header("Accept: */*")
    public interface TestInterface {

        @RequestLine("GET /target")
        CompletableFuture<String> asyncCompletableFutureGET();

        @RequestLine("GET /target")
        Future<String> asyncFutureGET();

        @RequestLine("GET /target")
        String syncGET();

        @RequestLine("POST /target")
        CompletableFuture<String> asyncCompletableFuturePOST(String body);

        @RequestLine("POST /target")
        Future<String> asyncFuturePOST(String body);

        @RequestLine("POST /target")
        String syncPOST(String body);

    }

    private static class MockClient implements Client {

        @Override
        public void request(SerializedRequest request, ClientCallback callback) {

            try {
                ContentCallback contentCallback = callback.headers(ResponseHeaders.builder().build());

                String response;

                if (request.getRequestBody() != null) {
                    response = new String(request.getRequestBody().getBody().array(), UTF_8);
                } else {
                    response = "Hello world!";
                }

                // call with individual bytes to test multiple invocations of content
                for (byte b : response.getBytes(UTF_8)) {
                    contentCallback.content(ByteBuffer.wrap(new byte[]{b}));
                }

            } finally {
                callback.complete();
            }
        }
    }

    @Test(timeout = 5000)
    public void name() throws Exception {
        TestInterface test = new ScreechBuilder(new MockClient())
                .build(TestInterface.class, new HardCodedTarget("http://localhost"));

        assertThat(test.syncGET()).isEqualTo("Hello world!");
        assertThat(test.asyncFutureGET().get()).isEqualTo("Hello world!");
        assertThat(test.asyncCompletableFutureGET().get()).isEqualTo("Hello world!");

        assertThat(test.syncPOST("foo")).isEqualTo("foo");
        assertThat(test.asyncFuturePOST("bar").get()).isEqualTo("bar");
        assertThat(test.asyncCompletableFuturePOST("baz").get()).isEqualTo("baz");
    }

    @Test
    @SuppressWarnings({"ConstantConditions", "EqualsWithItself"})
    public void testStandardObjectMethodsOnBuiltClient() {
        TestInterface test = new ScreechBuilder(new MockClient())
                .build(TestInterface.class, new HardCodedTarget("http://localhost"));

        TestInterface test2 = new ScreechBuilder(new MockClient())
                .build(TestInterface.class, new HardCodedTarget("http://localhost:8080"));

        log.info("toString: {}", test.toString());
        log.info("hasCode[test]: {}", test.hashCode());
        log.info("hasCode[test2]: {}", test2.hashCode());

        assertThat(test.hashCode()).isNotEqualTo(0);

        assertThat(test.toString()).isNotNull();

        assertThat(test.equals(null)).isFalse();
        assertThat(test.equals(new Object())).isFalse();

        assertThat(test.equals(test)).isTrue();

        assertThat(test.equals(test2)).isFalse();
    }
}