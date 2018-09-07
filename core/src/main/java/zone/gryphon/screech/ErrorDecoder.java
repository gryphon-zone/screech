package zone.gryphon.screech;

import java.util.function.Consumer;

public interface ErrorDecoder {

    void decode(SerializedResponse response, Consumer<Response<?>> callback);

    class DefaultErrorDecoder implements ErrorDecoder {

        @Override
        public void decode(SerializedResponse response, Consumer<Response<?>> callback) {
            throw new IllegalArgumentException("ASDFASDFASFD");
        }
    }

}
