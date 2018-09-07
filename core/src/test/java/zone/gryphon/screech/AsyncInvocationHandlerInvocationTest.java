package zone.gryphon.screech;


import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.anyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.Assertions.in;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@Slf4j
public class AsyncInvocationHandlerInvocationTest {

    private RequestEncoder requestEncoder = mock(RequestEncoder.class);

    private List<RequestInterceptor<?, ?, ?>> requestInterceptors;

    private ResponseDecoder responseDecoder = mock(ResponseDecoder.class);

    private ErrorDecoder errorDecoder = mock(ErrorDecoder.class);

    private Client client = mock(Client.class);

    private Target target = mock(Target.class);

    @Before
    public void setup() {
        reset(requestEncoder, responseDecoder, errorDecoder, client, target);
        requestInterceptors = new ArrayList<>();
    }

    @Header("Content-Type: application/json")
    @Header("X-Class-Header: {classHeader}")
    public interface ClassLevelHeader {

        class CustomExpander implements Param.Expander {

            @Override
            public <T> String expand(T input) {
                return "customExpanderResult";
            }
        }

        @SuppressWarnings("unused")
        @Header("X-Method-Header: {methodHeader}")
        @RequestLine("POST /foo/{bar}?baz={baz}")
        String foo(
                @Param("classHeader") String classHeader,
                @Param("methodHeader") String methodHeader,
                @Param("bar") String bar,
                @Param(value = "baz", expander = CustomExpander.class) String baz,
                String body);

    }

    @Test
    public void testClassLevelHeader() throws Exception {

        ClassLevelHeader instance = new InstanceBuilder(client)
                .errorDecoder(new ErrorDecoder.DefaultErrorDecoder())
                .requestEncoder(new RequestEncoder.StringRequestEncoder())
                .responseDecoder(new ResponseDecoder.StringResponseDecoder())
                .addRequestInterceptors(requestInterceptors)
                .build(ClassLevelHeader.class, target);

        doReturn("http://localhost").when(target).getTarget();

        SerializedResponse response = SerializedResponse.builder()
                .status(200)
                .headers(Collections.singletonList(new HttpParam("Content-Type", "application/json")))
                .responseBody(ResponseBody.builder()
                        .contentType("application/json")
                        .encoding(UTF_8.name())
                        .body(ByteBuffer.wrap("Hello world!".getBytes(UTF_8)))
                        .build())
                .build();

        doReturn(CompletableFuture.completedFuture(response)).when(client).request(any());

        String result = instance.foo("classHeaderValue", "methodHeaderValue", "barValue", "bazValue", "request body");

        assertThat(result).isEqualTo("Hello world!");

        SerializedRequest expectedRequest = SerializedRequest.builder()
                .method("POST")
                .uri(URI.create("http://localhost/foo/barValue"))
                .headers(Arrays.asList(new HttpParam("Content-Type", "application/json"), new HttpParam("X-Class-Header", "classHeaderValue"), new HttpParam("X-Method-Header", "methodHeaderValue")))
                .queryParams(Collections.singletonList(new HttpParam("baz", "customExpanderResult")))
                .requestBody(RequestBody.builder().contentType("application/json").body(ByteBuffer.wrap("request body".getBytes(UTF_8))).build())
                .build();

        verify(client).request(expectedRequest);

    }


}