package zone.gryphon.screech;

import java.util.concurrent.CompletableFuture;

public interface Client {

    CompletableFuture<SerializedResponse> request(SerializedRequest request);
}
