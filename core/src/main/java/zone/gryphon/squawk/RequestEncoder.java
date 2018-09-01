package zone.gryphon.squawk;

import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

public interface RequestEncoder {

    <T> CompletableFuture<OutputStream> encode(T entity);
}
