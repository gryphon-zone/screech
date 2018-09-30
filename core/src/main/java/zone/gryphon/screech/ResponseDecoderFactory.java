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

import zone.gryphon.screech.model.ResponseHeaders;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public interface ResponseDecoderFactory {

    ResponseDecoder create(ResponseHeaders response, Type type, Callback<Object> callback);

    class ErrorResponseDecoderFactory implements ResponseDecoderFactory {

        @Override
        public ResponseDecoder create(ResponseHeaders response, Type type, Callback<Object> callback) {
            return new ResponseDecoder() {

                @Override
                public void content(ByteBuffer content) {

                }

                @Override
                public void complete() {
                    callback.onFailure(new RuntimeException("We got errors, sir"));
                }

            };
        }
    }

    class SuccessResponseDecoderFactory implements ResponseDecoderFactory {

        @Override
        public ResponseDecoder create(ResponseHeaders response, Type type, Callback<Object> callback) {
            return new ResponseDecoder() {

                private final List<ByteBuffer> buffers = new ArrayList<>();

                @Override
                public void content(ByteBuffer content) {
                    buffers.add(content);
                }

                @Override
                public void complete() {
                    ByteBuffer buffer = ByteBuffer.allocate(buffers.stream().mapToInt(ByteBuffer::limit).sum());

                    for (ByteBuffer byteBuffer : buffers) {
                        buffer.put(byteBuffer);
                    }

                    callback.onSuccess(new String(buffer.array()));
                }

            };
        }
    }
}
