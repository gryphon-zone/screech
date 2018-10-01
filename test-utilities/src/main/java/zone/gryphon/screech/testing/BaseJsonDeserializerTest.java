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
import org.junit.Test;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseHeaders;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

    private ResponseHeaders successfulResponse() {
        return ResponseHeaders.builder()
                .headers(Collections.singletonList(new HttpParam("Content-Type", "application/json")))
                .status(200)
                .build();
    }

    @Test
    public void sanityTest() {

        @SuppressWarnings("unchecked")
        Callback<Object> callback = mock(Callback.class);

        ResponseDecoder responseDecoder = createFactory().create(successfulResponse(), MutableWidget.class, callback);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        responseDecoder.content(ByteBuffer.wrap("{\"foo\":\"bar\"}".getBytes(UTF_8)));
        responseDecoder.complete();

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(1)).onSuccess(new MutableWidget("bar"));
    }

    @Test
    public void testListDeserialization() {

        Type listType = new TypeToken<List<MutableWidget>>() {
            // empty
        }.getType();

        @SuppressWarnings("unchecked")
        Callback<Object> callback = mock(Callback.class);

        ResponseDecoder responseDecoder = createFactory().create(successfulResponse(), listType, callback);

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(0)).onSuccess(any());

        responseDecoder.content(ByteBuffer.wrap("[{\"foo\":\"bar\"},{\"foo\":\"baz\"},{\"foo\":\"bibbly\"}]".getBytes(UTF_8)));
        responseDecoder.complete();

        verify(callback, times(0)).onFailure(any());
        verify(callback, times(1)).onSuccess(Arrays.asList(new MutableWidget("bar"), new MutableWidget("baz"), new MutableWidget("bibbly")));
    }
}
