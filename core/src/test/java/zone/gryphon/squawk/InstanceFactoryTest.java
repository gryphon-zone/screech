package zone.gryphon.squawk;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Slf4j
public class InstanceFactoryTest {

    public interface TestInterface {

        @RequestLine("GET /target")
        CompletableFuture<String> async(String foo);

        @RequestLine("GET /target")
        Future<String> asyncFuture(String foo);

        @RequestLine("GET /target")
        String sync(String foo);

    }

    private static class MockClient implements Client {

        @Override
        public CompletableFuture<SerializedResponse> request(SerializedRequest request) {
            return CompletableFuture.completedFuture(SerializedResponse.builder().build());
        }
    }

    @Test
    public void name() {

        TestInterface test =  new InstanceFactory(new MockClient()).build(TestInterface.class, new HardCodedTarget("http://localhost"));

        log.info("Result of sync method: {}", test.sync("foobar"));

        log.info("Result of async method: {}", test.async("foobar"));

        log.info("Result of async future method: {}", test.asyncFuture("foobar"));
    }
}