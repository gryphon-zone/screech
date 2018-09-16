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

import zone.gryphon.screech.model.ResponseBody;
import zone.gryphon.screech.model.SerializedResponse;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

public interface ResponseDecoder {

    void decode(SerializedResponse response, Type type, Callback<Object> callback);

    class StringResponseDecoder implements ResponseDecoder {

        @Override
        public void decode(SerializedResponse response, Type type, Callback<Object> callback) {

            if (response.getStatus() == 404) {
                callback.onSuccess(null);
                return;
            }

            if (response.getResponseBody() == null) {
                callback.onSuccess(null);
                return;
            }

            ByteBuffer responseBody = response.getResponseBody().getBuffer();

            if (byte[].class.equals(type)) {
                callback.onSuccess(responseBody.array());
                return;
            }

            if (ByteBuffer.class.equals(type)) {
                callback.onSuccess(responseBody);
                return;
            }

            try {
                callback.onSuccess(new String(responseBody.array(), Optional.ofNullable(response.getResponseBody().getEncoding()).orElse(UTF_8.name())));
            } catch (Exception e) {
                callback.onError(new IllegalArgumentException("Unknown encoding " + response.getResponseBody().getEncoding(), e));
            }
        }
    }
}
