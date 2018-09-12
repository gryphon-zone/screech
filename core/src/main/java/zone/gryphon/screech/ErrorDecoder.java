package zone.gryphon.screech;

import zone.gryphon.screech.model.Response;
import zone.gryphon.screech.model.SerializedResponse;

public interface ErrorDecoder {

    void decode(SerializedResponse response, Callback<Response<?>> callback);

    class DefaultErrorDecoder implements ErrorDecoder {

        @Override
        public void decode(SerializedResponse response, Callback<Response<?>> callback) {
            throw new IllegalArgumentException("ASDFASDFASFD");
        }
    }

}
