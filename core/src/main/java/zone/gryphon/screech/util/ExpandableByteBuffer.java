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

package zone.gryphon.screech.util;

import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class ExpandableByteBuffer {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.wrap(new byte[0]);

    public static ExpandableByteBuffer createEmpty() {
        return create(0);
    }

    public static ExpandableByteBuffer create(long initialSize) {

        if (initialSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Unable to allocate a buffer of size " + initialSize);
        }

        return new ExpandableByteBuffer(Math.toIntExact(initialSize));
    }

    public static ExpandableByteBuffer create(int initialSize) {
        return new ExpandableByteBuffer(initialSize);
    }

    private volatile ByteBuffer buffer;

    private ExpandableByteBuffer(int initialSize) {

        if (initialSize < 0) {
            throw new IllegalArgumentException("initialSize cannot be negative");
        }

        if (initialSize == 0) {
            this.buffer = EMPTY_BUFFER;
        } else {
            this.buffer = ByteBuffer.allocate(initialSize);
        }
    }

    private ByteBuffer resize(int additionalCapacity) {
        ByteBuffer b = ByteBuffer.allocate(buffer.capacity() + additionalCapacity);

        // need to cast to a buffer because of a breaking change in JDK9:
        // https://github.com/plasma-umass/doppio/issues/497#issuecomment-334740243
        //noinspection RedundantCast
        ((Buffer) buffer).position(0);

        b.put(buffer);
        return b;
    }

    public ExpandableByteBuffer append(ByteBuffer content) {

        if ((buffer.capacity() - buffer.position()) < content.remaining()) {
            buffer = resize(content.remaining());
        }

        buffer.put(content);


        return this;
    }

    public void clear() {
        // need to cast to a buffer because of a breaking change in JDK9:
        // https://github.com/plasma-umass/doppio/issues/497#issuecomment-334740243
        //noinspection RedundantCast
        ((Buffer) this.buffer).clear();
    }

    public InputStream createInputStream() {
        return new ByteBufferInputStream(buffer);
    }
}
