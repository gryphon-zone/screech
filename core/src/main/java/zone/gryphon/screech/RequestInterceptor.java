package zone.gryphon.screech;

import zone.gryphon.screech.model.Request;
import zone.gryphon.screech.model.Response;

import java.util.function.BiConsumer;

public interface RequestInterceptor {

    <X, Y> void intercept(
            Request<X> request,
            BiConsumer<Request<?>, Callback<Response<Y>>> callback,
            Callback<Response<?>> responseCallback);

    class PassThroughRequestInterceptor implements RequestInterceptor {

        @Override
        public <X, Y> void intercept(
                Request<X> request,
                BiConsumer<Request<?>, Callback<Response<Y>>> callback,
                Callback<Response<?>> responseCallback) {

            callback.accept(request, (FunctionalCallback<Response<Y>>) (result, e) -> {
                if (e != null) {
                    responseCallback.onError(e);
                } else {
                    responseCallback.onSuccess(result);
                }
            });
        }
    }

}
