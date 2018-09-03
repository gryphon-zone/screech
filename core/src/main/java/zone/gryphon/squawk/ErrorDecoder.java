package zone.gryphon.squawk;

import java.util.concurrent.CompletableFuture;

public interface ErrorDecoder {

    CompletableFuture<Response<?>> decode(SerializedResponse response);

    class DefaultErrorDecoder implements ErrorDecoder {

        @Override
        public CompletableFuture<Response<?>> decode(SerializedResponse response) {
            CompletableFuture<Response<?>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("ASDFASDFASDF"));
            return future;
        }
    }

}
