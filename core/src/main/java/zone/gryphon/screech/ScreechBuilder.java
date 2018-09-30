/*
 * Copyright 2018-2018 Gryphon Zone
 *
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
 *
 */

package zone.gryphon.screech;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import zone.gryphon.screech.util.AsyncInvocationHandler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ScreechBuilder {

    private RequestEncoder requestEncoder = new RequestEncoder.StringRequestEncoder();

    private List<RequestInterceptor> requestInterceptors = new ArrayList<>();

    private ResponseDecoderFactory responseDecoder = new ResponseDecoderFactory.SuccessResponseDecoderFactory();

    private ResponseDecoderFactory errorDecoder = new ResponseDecoderFactory.ErrorResponseDecoderFactory();

    private Client client;

    public ScreechBuilder(@NonNull Client client) {
        this.client = client;
    }

    public ScreechBuilder requestEncoder(@NonNull RequestEncoder requestEncoder) {
        this.requestEncoder = requestEncoder;
        return this;
    }

    public ScreechBuilder addRequestInterceptor(@NonNull RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return this;
    }

    public ScreechBuilder addRequestInterceptors(@NonNull Iterable<RequestInterceptor> requestInterceptors) {
        requestInterceptors.forEach(this::addRequestInterceptor);
        return this;
    }

    public ScreechBuilder responseDecoder(@NonNull ResponseDecoderFactory responseDecoder) {
        this.responseDecoder = responseDecoder;
        return this;
    }

    public ScreechBuilder errorDecoder(@NonNull ResponseDecoderFactory errorDecoder) {
        this.errorDecoder = errorDecoder;
        return this;
    }

    public <T> T build(Class<T> clazz, Target target) {

        Map<Method, InvocationHandler> map = new HashMap<>();

        for (Method method : clazz.getMethods()) {
            map.put(method, AsyncInvocationHandler.from(method, requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target));
        }

        InvocationHandler handler = (proxy, method, args) -> map.get(method).invoke(proxy, method, args);

        //noinspection unchecked
        T proxy = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);

        try {
            final String toStringValue = String.format("%s{%s}", clazz.getSimpleName(), target.getClass().getSimpleName());
            Method toString = Object.class.getDeclaredMethod("toString");
            map.put(toString, (x, y, z) -> toStringValue);

            final int hashCodeValue = Objects.hash(map);
            Method hashCode = Object.class.getDeclaredMethod("hashCode");
            map.put(hashCode, (x, y, z) -> hashCodeValue);

            Method equals = Object.class.getDeclaredMethod("equals", Object.class);
            map.put(equals, (x, y, z) -> z[0] == proxy);

        } catch (Exception e) {
            throw new RuntimeException("Failed to construct proxy", e);
        }

        return proxy;
    }

}
