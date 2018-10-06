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
import zone.gryphon.screech.RequestEncoder;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
public abstract class BaseJsonSerializerTest {

    static {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MutableWidget {

        private String foo;

    }

    @Value
    @AllArgsConstructor
    public static class ImmutableWidget {

        private final String foo;

    }

    @Data
    @NoArgsConstructor
    @SuppressWarnings("unused")
    public static class NonSerializableObject {

        public String toString() {
            throw new RuntimeException("mock exception for test");
        }

    }

    protected abstract RequestEncoder createEncoder();

    private RequestEncoder requestEncoder;

    @Before
    public void setUp() {
        this.requestEncoder = createEncoder();
        log.info("Running test {} using {}", testName.getMethodName(), requestEncoder);
    }


    @Rule
    public final TestName testName = new TestName();

    private Callback<ByteBuffer> setupCallback(AtomicReference<String> ref) {

        @SuppressWarnings("unchecked")
        Callback<ByteBuffer> callback = mock(Callback.class);

        doAnswer(invocationOnMock -> {
            log.error("Unexpected exception", invocationOnMock.getArguments()[0]);
            return null;
        }).when(callback).onFailure(any());

        doAnswer(invocationOnMock -> {
            ref.set(toString(invocationOnMock.getArgumentAt(0, ByteBuffer.class)));
            return null;
        }).when(callback).onSuccess(any());

        return callback;

    }

    private String toString(ByteBuffer buffer) {
        byte[] copy = new byte[buffer.remaining()];
        buffer.duplicate().get(copy);
        return new String(copy, UTF_8);
    }

    private void testSuccessfulSerialization(Object o, String expected) {
        AtomicReference<String> buffer = new AtomicReference<>();

        Callback<ByteBuffer> callback = setupCallback(buffer);

        requestEncoder.encode(o, callback);

        // wait up to 5 seconds for results, in case serializer is async
        verify(callback, timeout(Duration.ofSeconds(5).toMillis())).onSuccess(any());
        verify(callback, times(0)).onFailure(any());
        assertThat(buffer.get()).isEqualTo(expected);
    }

    private void testFailedDeserialization(Object o) {
        @SuppressWarnings("unchecked")
        Callback<ByteBuffer> callback = mock(Callback.class);

        doAnswer(invocationOnMock -> {
            log.debug("Caught expected exception", invocationOnMock.getArguments()[0]);
            return null;
        }).when(callback).onFailure(any());

        doAnswer(invocationOnMock -> {
            log.error("onSuccess() called when it should not have been: {}",
                    toString(invocationOnMock.getArgumentAt(0, ByteBuffer.class)));
            return null;
        }).when(callback).onSuccess(any());

        requestEncoder.encode(o, callback);

        // wait up to 5 seconds for results, in case serializer is async
        verify(callback, timeout(Duration.ofSeconds(5).toMillis())).onFailure(any());
        verify(callback, times(0)).onSuccess(any());
    }

    @Test
    public void testSerializeNonSerializableObject() {
        testFailedDeserialization(Collections.singletonMap(new NonSerializableObject(), "foo"));
    }

    @Test
    public void testSerializeMutableObject() {
        String expected = "{\"foo\":\"bar\"}";
        testSuccessfulSerialization(new MutableWidget("bar"), expected);
    }

    @Test
    public void testSerializeImmutableObject() {
        String expected = "{\"foo\":\"bar\"}";
        testSuccessfulSerialization(new ImmutableWidget("bar"), expected);
    }

    @Test
    public void testSerializeMap() {
        String expected = "{\"foo\":\"bar\"}";
        testSuccessfulSerialization(Collections.singletonMap("foo", "bar"), expected);
    }

    @Test
    public void testSerializeList() {
        String expected = "[{\"foo\":\"bar\"},{\"foo\":\"baz\"}]";
        testSuccessfulSerialization(Arrays.asList(new MutableWidget("bar"), new MutableWidget("baz")), expected);
    }

    @Test
    public void testSerializeSet() {
        String expected = "[{\"foo\":\"bar\"}]";
        testSuccessfulSerialization(Collections.singleton(new MutableWidget("bar")), expected);
    }

    @Test
    public void testSerializeBooleanTrue() {
        testSuccessfulSerialization(true, Boolean.toString(true));
    }

    @Test
    public void testSerializeBooleanFalse() {
        testSuccessfulSerialization(false, Boolean.toString(false));
    }

    @Test
    public void testSerializeByteMaxValue() {
        testSuccessfulSerialization(Byte.MAX_VALUE, Byte.toString(Byte.MAX_VALUE));
    }

    @Test
    public void testSerializeByteMinValue() {
        testSuccessfulSerialization(Byte.MIN_VALUE, Byte.toString(Byte.MIN_VALUE));
    }

    @Test
    public void testSerializeShortMaxValue() {
        testSuccessfulSerialization(Short.MAX_VALUE, Short.toString(Short.MAX_VALUE));
    }

    @Test
    public void testSerializeShortMinValue() {
        testSuccessfulSerialization(Short.MIN_VALUE, Short.toString(Short.MIN_VALUE));
    }

    @Test
    public void testSerializeIntegerMaxValue() {
        testSuccessfulSerialization(Integer.MAX_VALUE, Integer.toString(Integer.MAX_VALUE));
    }

    @Test
    public void testSerializeIntegerMinValue() {
        testSuccessfulSerialization(Integer.MIN_VALUE, Integer.toString(Integer.MIN_VALUE));
    }

    @Test
    public void testSerializeLongMaxValue() {
        testSuccessfulSerialization(Long.MAX_VALUE, Long.toString(Long.MAX_VALUE));
    }

    @Test
    public void testSerializeLongMinValue() {
        testSuccessfulSerialization(Long.MIN_VALUE, Long.toString(Long.MIN_VALUE));
    }

    @Test
    public void testSerializeZero() {
        testSuccessfulSerialization(0, Integer.toString(0));
    }

    @Test
    public void testSerializePositiveDouble() {
        testSuccessfulSerialization(0.5, Double.toString(0.5));
    }

    @Test
    public void testSerializeNegativeDouble() {
        testSuccessfulSerialization(-0.5, Double.toString(-0.5));
    }

}
