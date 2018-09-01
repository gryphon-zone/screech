package zone.gryphon.squawk;

import java.util.concurrent.CompletableFuture;

public interface Client {

    CompletableFuture<SerializedResponse> request(SerializedRequest request);
}
