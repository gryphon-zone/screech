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
            callback.onSuccess(SerializedResponse.builder().responseBody(ResponseBody.builder().body(ByteBuffer.wrap("Hello world!".getBytes())).build()).build());
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