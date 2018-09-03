package zone.gryphon.squawk;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface ResponseDecoder {

    CompletableFuture<?> decode(SerializedResponse response, Type type);

    class StringResponseDecoder implements ResponseDecoder {

        @Override
        public CompletableFuture<?> decode(SerializedResponse response, Type type) {

            if (response.getStatus() == 404) {
                return CompletableFuture.completedFuture(null);
            }

            if (response.getResponseBody() == null) {
                return CompletableFuture.completedFuture(null);
            }

            if (byte[].class.equals(type)) {
                return CompletableFuture.completedFuture(response.getResponseBody().getBody().array());
            }

            if (ByteBuffer.class.equals(type)) {
                return CompletableFuture.completedFuture(response.getResponseBody().getBody());
            }

            try {
                return CompletableFuture.completedFuture(new String(response.getResponseBody().getBody().array(), response.getResponseBody().getEncoding()));
            } catch (Exception e) {
                throw new IllegalStateException("Unknown encoding " + response.getResponseBody().getEncoding());
            }
        }

    }
}
