package zone.gryphon.squawk;

import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class JettySquawkClientTest {

    static {
        SLF4JBridgeHandler.install();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
    }

    @Rule
    public MockWebServer mockWebServer = new MockWebServer();

    private JettySquawkClient client = new JettySquawkClient();

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void cleanup() throws Exception {
        client.close();
    }

    @Test
    public void simpleTest() throws Exception {

        int iterations = 1000000;

        AtomicLong sum = new AtomicLong(0);

        log.info("uri: {}", mockWebServer.url("/").uri());

        SerializedRequest request = SerializedRequest.builder()
                .method("GET")
                .uri(mockWebServer.url("/").uri())
                .build();

        for (int i = 0; i < iterations; i++) {
            mockWebServer.enqueue(new MockResponse());

            long start = System.nanoTime();
            client.request(request).thenApply(ignored -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                    .thenAccept(sum::addAndGet)
                    .get();
        }


        log.warn("Average response time: {} ms", sum.get() / (double) iterations);
    }
}