package zone.gryphon.screech;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface RequestInterceptor {

    <X, Y> BiConsumer<Response<Y>, Consumer<Response<?>>> intercept(Request<X> request, Consumer<Request<?>> callback);

    class PassThroughRequestInterceptor implements RequestInterceptor {

        @Override
        public <X, Y> BiConsumer<Response<Y>, Consumer<Response<?>>> intercept(Request<X> request, Consumer<Request<?>> callback) {
            callback.accept(request);

            return (response, responseCallback) -> responseCallback.accept(response);
        }
    }

}
