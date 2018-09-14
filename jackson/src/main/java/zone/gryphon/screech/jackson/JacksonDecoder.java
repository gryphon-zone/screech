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

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.model.SerializedResponse;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class JacksonDecoder implements ResponseDecoder {

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

            ByteBuffer buffer = response.getResponseBody().getBody();

            // if response is backed by an array, use it directly
            if (buffer.hasArray()) {
                callback.onSuccess(objectMapper.readValue(buffer.array(), objectMapper.constructType(type)));
            } else {
                callback.onError(new RuntimeException("Non heap byte buffers not yet supported"));
            }

        } catch (Exception e) {
            callback.onError(e);
        }
    }
}
