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

package zone.gryphon.screech.jaxb2;

import lombok.NonNull;
import zone.gryphon.screech.Callback;
import zone.gryphon.screech.RequestEncoder;
import zone.gryphon.screech.exception.EncodeException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class Jaxb2Encoder implements RequestEncoder {

    private final JAXBContextFactory jaxbContextFactory;

    public Jaxb2Encoder(@NonNull JAXBContextFactory jaxbContextFactory) {
        this.jaxbContextFactory = jaxbContextFactory;
    }

    @Override
    public <T> void encode(T entity, Callback<ByteBuffer> callback) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            jaxbContextFactory.marshallerFor(entity.getClass()).marshal(entity, stream);
            callback.onSuccess(ByteBuffer.wrap(stream.toByteArray()));
        } catch (Throwable e) {
            callback.onFailure(new EncodeException("Failed to serialize entity", e));
        }
    }
}
