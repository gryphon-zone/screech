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

package zone.gryphon.screech.util;

import lombok.NonNull;
import zone.gryphon.screech.ResponseDecoder;
import zone.gryphon.screech.model.ResponseHeaders;

import java.nio.ByteBuffer;

public abstract class BufferingResponseDecoder implements ResponseDecoder {

    private final ExpandableByteBuffer buffer;

    public BufferingResponseDecoder(@NonNull ResponseHeaders responseHeaders) {
        this.buffer = responseHeaders
                .getContentLength()
                .map(ExpandableByteBuffer::create)
                .orElseGet(ExpandableByteBuffer::createEmpty);
    }

    @Override
    public void content(ByteBuffer content) {

        if (content == null || content.remaining() == 0) {
            return;
        }

        buffer.append(content);
    }

    @Override
    public void complete() {
        complete(buffer);
    }

    @Override
    public void abort() {
        this.buffer.clear();
    }

    protected abstract void complete(ExpandableByteBuffer byteBuffer);
}
