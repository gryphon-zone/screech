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
import org.junit.Test;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseHeaders;

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
public abstract class BaseJsonDeserializerTest {

    protected abstract ResponseDecoderFactory createFactory();

    public abstract class TypeToken<T> {

        public Type getType() {
            return ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MutableWidget {

        private String foo;

    }

    @Value
    public static class ImmutableWidget {

        private final String foo;

        @ConstructorProperties("foo")
        public ImmutableWidget(String foo) {
            this.foo = foo;
        }

    }

    private ResponseHeaders successfulResponse() {
        return ResponseHeaders.builder()
                .headers(Collections.singletonList(new HttpParam("Content-Type", "application/json")))
                .status(200)
                .build();
    }

    private void testSuccessfulDeserialization(Type type, String value, Object expectedResult) {
        Callback<Object> callback = setupCallback();

        ResponseDecoder responseDecoder = createFactory().create(successfulResponse(), type, callback);

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

        ResponseDecoder responseDecoder = createFactory().create(successfulResponse(), type, callback);

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

    @Test
    public void testMutableObjectDeserialization() {
        String json = "{\"foo\":\"bar\"}";

        Object expectedResult = new MutableWidget("bar");

        testSuccessfulDeserialization(MutableWidget.class, json, expectedResult);
    }

    @Test
    public void testImmutableObjectDeserialization() {
        String json = "{\"foo\":\"bar\"}";

        Object expectedResult = new ImmutableWidget("bar");

        testSuccessfulDeserialization(ImmutableWidget.class, json, expectedResult);
    }

    @Test
    public void testListDeserialization() {
        Type type = new TypeToken<List<MutableWidget>>() {
            // empty
        }.getType();

        String json = "[{\"foo\":\"bar\"},{\"foo\":\"baz\"},{\"foo\":\"bibbly\"}]";

        Object expectedResult = Arrays.asList(new MutableWidget("bar"), new MutableWidget("baz"), new MutableWidget("bibbly"));

        testSuccessfulDeserialization(type, json, expectedResult);
    }

    @Test
    public void testSetDeserialization() {
        Type type = new TypeToken<Set<MutableWidget>>() {
            // empty
        }.getType();

        String json = "[{\"foo\":\"bar\"},{\"foo\":\"baz\"},{\"foo\":\"bibbly\"}]";

        Object expectedResult = new HashSet<>(Arrays.asList(new MutableWidget("bar"), new MutableWidget("baz"), new MutableWidget("bibbly")));

        testSuccessfulDeserialization(type, json, expectedResult);
    }

    @Test
    public void testMapDeserialization() {
        Type type = new TypeToken<Map<String, MutableWidget>>() {
            // empty
        }.getType();

        String json = "{\"bar\": {\"foo\":\"baz\"}}";

        Object expectedResult = Collections.singletonMap("bar", new MutableWidget("baz"));

        testSuccessfulDeserialization(type, json, expectedResult);
    }

    @Test
    public void testStringDeserialization() {
        testSuccessfulDeserialization(String.class, "\"foo\"", "foo");
    }

    @Test
    public void testBooleanDeserialization() {
        testSuccessfulDeserialization(Boolean.class, Boolean.toString(Boolean.TRUE), Boolean.TRUE);
    }

    @Test
    public void testByteDeserialization() {
        testSuccessfulDeserialization(Byte.class, Byte.toString(Byte.MAX_VALUE), Byte.MAX_VALUE);
    }

    @Test
    public void testShortDeserialization() {
        testSuccessfulDeserialization(Short.class, Short.toString(Short.MAX_VALUE), Short.MAX_VALUE);
    }

    @Test
    public void testIntegerDeserialization() {
        testSuccessfulDeserialization(Integer.class, Integer.toString(Integer.MAX_VALUE), Integer.MAX_VALUE);
    }

    @Test
    public void testLongDeserialization() {
        testSuccessfulDeserialization(Long.class, Long.toString(Long.MAX_VALUE), Long.MAX_VALUE);
    }

    @Test
    public void testWithInvalidJsonMissingHalf() {
        String json = "{\"foo\"";

        testFailedDeserialization(MutableWidget.class, json);
    }

    @Test
    public void testWithInvalidJsonNoValueInObject() {
        String json = "{\"foo\"}";

        testFailedDeserialization(MutableWidget.class, json);
    }

    @Test
    public void testWithInvalidJsonIncorrectType() {
        String json = "123";

        testFailedDeserialization(MutableWidget.class, json);
    }

    @Test
    public void tesAbortCallsNothing() {
        Callback<Object> callback = setupCallback();

        ResponseDecoder responseDecoder = createFactory().create(successfulResponse(), MutableWidget.class, callback);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        responseDecoder.abort();

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());
    }

    @Test
    public void tesAbortAfterContentCallsNothing() {
        Callback<Object> callback = setupCallback();

        ResponseDecoder responseDecoder = createFactory().create(successfulResponse(), MutableWidget.class, callback);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        responseDecoder.content(ByteBuffer.wrap("{\"foo\":\"bar\"}".getBytes(UTF_8)));
        responseDecoder.abort();

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());
    }
}
