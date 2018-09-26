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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.model.HttpParam;
import zone.gryphon.screech.model.ResponseHeaders;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class JacksonDecoder implements ResponseDecoder {

    private final ObjectMapper objectMapper;

    private final ResponseHeaders responseHeaders;

    private final Type type;

    private final Callback<Object> callback;

    private volatile ByteBuffer buffer = null;

    JacksonDecoder(ObjectMapper objectMapper, ResponseHeaders responseHeaders, Type type, Callback<Object> callback) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.responseHeaders = responseHeaders;
        this.type = Objects.requireNonNull(type, "type");
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    private ByteBuffer init(int potentialInitialCapacity) {
        int length = Optional.ofNullable(responseHeaders.getHeaders()).orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull)
                .filter(header -> "content-length".equalsIgnoreCase(header.getKey()))
                .findAny()
                .map(HttpParam::getValue)
                .map(this::parse)
                .orElse(-1);

        if (length > 0) {
            return ByteBuffer.allocate(length);
        }

        return ByteBuffer.allocate(potentialInitialCapacity);
    }

    private ByteBuffer resize(int additionalCapacity) {
        ByteBuffer b = ByteBuffer.allocate(buffer.capacity() + additionalCapacity);
        buffer.position(0);
        b.put(buffer);
        return b;
    }

    @Override
    public void onContent(ByteBuffer content) {
        if (buffer == null) {
            buffer = init(content.remaining());
        }

        if ((buffer.capacity() - buffer.position()) < content.remaining()) {
            buffer = resize(content.remaining());
        }

        buffer.put(content);
    }

    @Override
    public void onComplete() {
        try {
            callback.onSuccess(objectMapper.readValue(buffer.array(), buffer.arrayOffset(), buffer.arrayOffset() + buffer.limit(), objectMapper.constructType(type)));
        } catch (Throwable t) {
            callback.onError(t);
        }
    }

    @Override
    public void abort() {
        this.buffer.clear();
    }

    private Integer parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }
}
