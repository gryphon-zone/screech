package zone.gryphon.screech;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface RequestEncoder {

    <T> void encode(T entity, Callback<ByteBuffer> callback);

    class StringRequestEncoder implements RequestEncoder {

        @Override
        public <T> void encode(T entity, Callback<ByteBuffer> callback) {
            callback.onSuccess(ByteBuffer.wrap(String.valueOf(entity).getBytes(StandardCharsets.UTF_8)));
        }
    }
}
