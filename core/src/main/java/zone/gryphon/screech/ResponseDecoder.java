package zone.gryphon.screech;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

public interface ResponseDecoder {

    void decode(SerializedResponse response, Type type, Consumer<Object> callback);

    class StringResponseDecoder implements ResponseDecoder {

        @Override
        public void decode(SerializedResponse response, Type type, Consumer<Object> callback) {

            if (response.getStatus() == 404) {
                callback.accept(null);
                return;
            }

            if (response.getResponseBody() == null) {
                callback.accept(null);
                return;
            }

            if (byte[].class.equals(type)) {
                callback.accept(response.getResponseBody().getBody().array());
                return;
            }

            if (ByteBuffer.class.equals(type)) {
                callback.accept(response.getResponseBody().getBody());
                return;
            }

            try {
                callback.accept(new String(response.getResponseBody().getBody().array(), Optional.ofNullable(response.getResponseBody().getEncoding()).orElse(StandardCharsets.UTF_8.name())));
            } catch (Exception e) {
                throw new IllegalStateException("Unknown encoding " + response.getResponseBody().getEncoding());
            }
        }

    }
}
