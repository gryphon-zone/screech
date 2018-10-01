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

package zone.gryphon.screech.jackson2;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.model.ResponseHeaders;

import java.lang.reflect.Type;
import java.util.Objects;

public class JacksonDecoderFactory implements ResponseDecoderFactory {

    private final ObjectMapper mapper;

    public JacksonDecoderFactory() {
        this(new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    public JacksonDecoderFactory(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public ResponseDecoder create(ResponseHeaders response, Type type, Callback<Object> callback) {
        return new JacksonDecoder(mapper, response, type, callback);
    }

}
