package zone.gryphon.screech;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface RequestEncoder {

    <T> void encode(T entity, Consumer<ByteBuffer> callback);

    class StringRequestEncoder implements RequestEncoder {

        @Override
        public <T> void encode(T entity, Consumer<ByteBuffer> callback) {
            callback.accept(ByteBuffer.wrap(String.valueOf(entity).getBytes(StandardCharsets.UTF_8)));
        }
    }
}
