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

package zone.gryphon.screech.testing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.bridge.SLF4JBridgeHandler;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseHeaders;

import javax.xml.bind.annotation.XmlRootElement;
import java.beans.ConstructorProperties;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
public abstract class BaseXmlDeserializerTest {

    static {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
    }

    @SuppressWarnings("unused")
    public abstract class TypeToken<T> {

        public Type getType() {
            return ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @XmlRootElement
    public static class MutableWidget {

        private String foo;

    }

    protected abstract ResponseDecoderFactory createFactory();

    @Rule
    public final TestName testName = new TestName();

    private ResponseDecoderFactory factory;

    @Before
    public void setUp() {
        this.factory = createFactory();
        log.info("Running test {} using {}", testName.getMethodName(), factory);
    }

    @Test
    public void testMutableObjectDeserialization() {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<mutableWidget><foo>bar</foo></mutableWidget>";

        Object expectedResult = new MutableWidget("bar");

        testSuccessfulDeserialization(MutableWidget.class, xml, expectedResult);
    }

    @Test
    public void testMutableWithInvalidXmlMissingHalf() {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<mutableWidget><foo>bar";

        testFailedDeserialization(MutableWidget.class, xml);
    }

    @Test
    public void testMutableWithInvalidXmlIncorrectType() {
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "123";

        testFailedDeserialization(MutableWidget.class, xml);
    }

    @Test
    public void testAbortCallsNothing() {
        Callback<Object> callback = setupCallback();

        ResponseDecoder responseDecoder = factory.create(successfulResponse(), MutableWidget.class, callback);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        responseDecoder.abort();

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());
    }

    @Test
    public void testAbortAfterContentCallsNothing() {
        Callback<Object> callback = setupCallback();

        ResponseDecoder responseDecoder = factory.create(successfulResponse(), MutableWidget.class, callback);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        responseDecoder.content(ByteBuffer.wrap("{\"foo\":\"bar\"}".getBytes(UTF_8)));
        responseDecoder.abort();

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());
    }

    private ResponseHeaders successfulResponse() {
        return ResponseHeaders.builder()
                .headers(Collections.singletonList(new HttpParam("Content-Type", "application/json")))
                .status(200)
                .build();
    }

    private void testSuccessfulDeserialization(Type type, String value, Object expectedResult) {
        Callback<Object> callback = setupCallback();

        ResponseDecoder responseDecoder = factory.create(successfulResponse(), type, callback);
        log.debug("Factory created response decoder {}", responseDecoder);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        // ensure that calling the method with empty byte buffers doesn't cause weird side effects.
        // 10 iterations is arbitrarily chosen
        for (int i = 0; i < 10; i++) {
            responseDecoder.content(ByteBuffer.allocate(0));
        }

        // sending the data as individual bytes tests that the deserializers can handle multiple invocations of `content`
        for (byte b : value.getBytes(UTF_8)) {
            responseDecoder.content(ByteBuffer.wrap(new byte[]{b}));
        }

        responseDecoder.complete();

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(1)).onSuccess(expectedResult);
    }

    private void testFailedDeserialization(Type type, String json) {
        @SuppressWarnings("unchecked")
        Callback<Object> callback = mock(Callback.class);

        doAnswer(invocationOnMock -> {
            log.debug("Caught expected exception", invocationOnMock.getArguments()[0]);
            return null;
        }).when(callback).onFailure(any());

        ResponseDecoder responseDecoder = factory.create(successfulResponse(), type, callback);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        responseDecoder.content(ByteBuffer.wrap(json.getBytes(UTF_8)));

        responseDecoder.complete();

        verify(callback, times(1)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());
    }

    private Callback<Object> setupCallback() {

        @SuppressWarnings("unchecked")
        Callback<Object> callback = mock(Callback.class);

        doAnswer(invocationOnMock -> {
            log.error("Unexpected exception", invocationOnMock.getArguments()[0]);
            return null;
        }).when(callback).onFailure(any());

        return callback;

    }
}
