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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.RequestBody;
import zone.gryphon.screech.model.ResponseBody;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.model.SerializedRequest;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
@Slf4j
@Ignore
public class AsyncInvocationHandlerInvocationTest {

    private RequestEncoder requestEncoder = mock(RequestEncoder.class);

    private List<RequestInterceptor> requestInterceptors;

    private ResponseDecoder responseDecoder = mock(ResponseDecoder.class);

//    private ErrorDecoder errorDecoder = mock(ErrorDecoder.class);

    private Client client = mock(Client.class);

    private Target target = mock(Target.class);

    @Before
    public void setup() {
//        reset(requestEncoder, responseDecoder, errorDecoder, client, target);
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
//                .errorDecoder(new ErrorDecoder.DefaultErrorDecoder())
                .requestEncoder(new RequestEncoder.StringRequestEncoder())
//                .responseDecoder(new ResponseDecoder.StringResponseDecoder())
                .addRequestInterceptors(requestInterceptors)
                .build(ClassLevelHeader.class, target);

        doReturn("http://localhost").when(target).getTarget();

        ResponseHeaders response = ResponseHeaders.builder()
                .status(200)
                .headers(Collections.singletonList(new HttpParam("Content-Type", "application/json")))
//                .responseBody(ResponseBody.builder()
//                        .contentType("application/json")
//                        .encoding(UTF_8.name())
//                        .buffer(ByteBuffer.wrap("Hello world!".getBytes(UTF_8)))
//                        .build())
                .build();

        doAnswer(invocation -> {
            ((Callback) invocation.getArguments()[1]).onSuccess(response);
            return null;
        }).when(client).request(any(), any());

        String result = instance.foo("classHeaderValue", "methodHeaderValue", "barValue", "bazValue", "request body");

        assertThat(result).isEqualTo("Hello world!");

        SerializedRequest expectedRequest = SerializedRequest.builder()
                .method("POST")
                .uri(URI.create("http://localhost/foo/barValue"))
                .headers(Arrays.asList(new HttpParam("Content-Type", "application/json"), new HttpParam("X-Class-Header", "classHeaderValue"), new HttpParam("X-Method-Header", "methodHeaderValue")))
                .queryParams(Collections.singletonList(new HttpParam("baz", "customExpanderResult")))
                .requestBody(RequestBody.builder().contentType("application/json").body(ByteBuffer.wrap("request body".getBytes(UTF_8))).build())
                .build();

        verify(client).request(eq(expectedRequest), any());

    }


}