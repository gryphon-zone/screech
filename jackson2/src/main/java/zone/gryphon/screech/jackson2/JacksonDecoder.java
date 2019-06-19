/*
 * Copyright 2019-2019 Gryphon Zone
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
 */

package zone.gryphon.screech.jackson2;

import com.fasterxml.jackson.databind.ObjectMapper;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.model.ResponseHeaders;
import zone.gryphon.screech.util.BufferingResponseDecoder;
import zone.gryphon.screech.util.ExpandableByteBuffer;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Objects;

public class JacksonDecoder extends BufferingResponseDecoder {

    private final ObjectMapper objectMapper;

    private final Type type;

    private final Callback<Object> callback;

    JacksonDecoder(ObjectMapper objectMapper, ResponseHeaders responseHeaders, Type type, Callback<Object> callback) {
        super(responseHeaders);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.type = Objects.requireNonNull(type, "type");
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    @Override
    protected void complete(ExpandableByteBuffer buffer) {
        // since backing buffer for stream is in-memory, it should never block, and therefore it should be safe to call
        try (InputStream inputStream = new BufferedInputStream(buffer.createInputStream())) {
            callback.onSuccess(objectMapper.readValue(inputStream, objectMapper.constructType(type)));
        } catch (Throwable t) {
            callback.onFailure(t);
        }
    }

    @Override
    public String toString() {
        return "JacksonDecoder{ObjectMapper@" + objectMapper.hashCode() + '}';
    }
}
