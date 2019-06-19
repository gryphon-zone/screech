/*
 * Copyright 2019-2019 Gryphon Zone
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zone.gryphon.screech;

import zone.gryphon.screech.model.Request;
import zone.gryphon.screech.model.Response;

import java.util.function.BiConsumer;

/**
 * Interface for an object which can intercept a request.
 * This API is intentionally generic, to allow a multitude of use cases to take advantage of it (and prevent the need
 * for a plethora of specialized interceptors).
 * <br><br>
 * <h2>Some example use cases:</h2>
 * <h3>Basic authorization interceptor</h3>
 * <pre>
 * void intercept(
 *     Request&lt;?&gt; request,
 *     BiConsumer&lt;Request&lt;?&gt;, Callback&lt;Response&lt;?&gt;&gt;&gt; callback,
 *     Callback&lt;Response&lt;?&gt;&gt; response) {
 *
 *         Request&lt;?&gt; modifiedRequest = request.toBuilder()
 *           .addHeader("Authorization", "Basic: " + auth)
 *           .build();
 *
 *         callback.accept(modifiedRequest, response);
 * }
 * </pre>
 * <h3>Token authorization interceptor</h3>
 * <pre>
 * void intercept(
 *     Request&lt;?&gt; request,
 *     BiConsumer&lt;Request&lt;?&gt;, Callback&lt;Response&lt;?&gt;&gt;&gt; callback,
 *     Callback&lt;Response&lt;?&gt;&gt; response) {
 *
 *         Request&lt;?&gt; modifiedRequest = request.toBuilder()
 *           .addHeader("Authorization", "Token: " + getToken())
 *           .build();
 *
 *         callback.accept(modifiedRequest, new FunctionalCallback() {
 *
 *             void onComplete(Response&lt;?&gt; result, Throwable e){
 *
 *               if (e != null) {
 *                   response.onFailure(e);
 *               } else {
 *
 *                   if (result.getHttpStatusCode() == 401) {
 *                       invalidateCachedToken();
 *                   }
 *
 *                   response.onComplete(result);
 *               }
 *             }
 *
 *         });
 * }
 * </pre>
 */
public interface RequestInterceptor {

    <X, Y> void intercept(
            Request<X> request,
            BiConsumer<Request<?>, Callback<Response<Y>>> callback,
            Callback<Response<?>> responseCallback);

}
