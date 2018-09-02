package zone.gryphon.squawk;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface RequestEncoder {

    <T> CompletableFuture<ByteBuffer> encode(T entity);
}
