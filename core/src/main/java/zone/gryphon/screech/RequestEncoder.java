package zone.gryphon.screech;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public interface RequestEncoder {

    <T> CompletableFuture<ByteBuffer> encode(T entity);

    class StringRequestEncoder implements RequestEncoder {

        @Override
        public <T> CompletableFuture<ByteBuffer> encode(T entity) {
            return CompletableFuture.completedFuture(ByteBuffer.wrap(String.valueOf(entity).getBytes(StandardCharsets.UTF_8)));
        }
    }
}
