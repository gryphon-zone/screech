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

package zone.gryphon.screech.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.model.SerializedResponse;
import zone.gryphon.screech.util.ByteBufferInputStream;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class JacksonDecoder implements ResponseDecoder {

    private static final Executor threadpool = Executors.newCachedThreadPool();

    private final ObjectMapper objectMapper;

    public JacksonDecoder(Module... modules) {
        this(Arrays.asList(modules));
    }

    public JacksonDecoder(Iterable<Module> modules) {
        this(new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES).registerModules(modules));
    }

    public JacksonDecoder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void decode(SerializedResponse response, Type type, Callback<Object> callback) {
        try {

            if (response.getResponseBody() == null) {
                callback.onSuccess(null);
                return;
            }

            ByteBuffer buffer = response.getResponseBody().getBuffer();

            JavaType javaType = objectMapper.constructType(type);

            // if response is backed by an array, use it directly. Since this is all in-memory, it should be non-blocking
            if (buffer.hasArray()) {
                callback.onSuccess(objectMapper.readValue(buffer.array(), javaType));
            } else {
                // since the buffer isn't backed by the heap, reading it may involve blocking IO to read data from
                // disk. This means that in order to avoid blocking the calling thread, we have to run the
                // deserialization in a background thread.
                threadpool.execute(() -> {
                    try (InputStream stream = new BufferedInputStream(new ByteBufferInputStream(buffer))) {
                        callback.onSuccess(objectMapper.readValue(stream, javaType));
                    } catch (Throwable e) {
                        callback.onError(e);
                    }
                });
            }
        } catch (Throwable e) {
            callback.onError(e);
        }
    }
}
