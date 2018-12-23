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

package zone.gryphon.screech.internal;

import lombok.EqualsAndHashCode;
import zone.gryphon.screech.Client;
import zone.gryphon.screech.RequestEncoder;
import zone.gryphon.screech.RequestInterceptor;
import zone.gryphon.screech.ResponseDecoderFactory;
import zone.gryphon.screech.Target;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@EqualsAndHashCode
public class ReflectiveScreech<T> implements InvocationHandler {

    private static final Method toStringMethod;

    private static final Method hashCodeMethod;

    private static final Method equalsMethod;


    static {
        try {
            toStringMethod = Object.class.getDeclaredMethod("toString");
            hashCodeMethod = Object.class.getDeclaredMethod("hashCode");
            equalsMethod = Object.class.getDeclaredMethod("equals", Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Unable to retrieve method from java.lang.Object, unable to construct reflective proxies", e);
        }
    }


    private final Map<Method, InvocationHandler> map = new HashMap<>();

    private final Class<T> proxyClass;

    public ReflectiveScreech(
            RequestEncoder requestEncoder,
            List<RequestInterceptor> requestInterceptors,
            ResponseDecoderFactory responseDecoder,
            ResponseDecoderFactory errorDecoder,
            Executor requestExecutor,
            Executor responseExecutor,
            Client client,
            Class<T> clazz,
            Target target) {
        this.proxyClass = clazz;

        for (Method method : clazz.getMethods()) {
            AsyncInvocationHandler handler = AsyncInvocationHandler.builder()
                    .method(method)
                    .encoder(requestEncoder)
                    .requestInterceptors(requestInterceptors)
                    .responseDecoder(responseDecoder)
                    .errorDecoder(errorDecoder)
                    .client(client)
                    .target(target)
                    .requestExecutor(requestExecutor)
                    .responseExecutor(responseExecutor)
                    .build();

            map.put(method, handler);
        }

        // equals method is always invoked with exactly one argument, hence args[0]
        map.put(equalsMethod, ((proxy, method, args) -> smartEquals(args[0])));
        map.put(hashCodeMethod, ((proxy, method, args) -> hashCode()));
        map.put(toStringMethod, ((proxy, method, args) -> toString()));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        InvocationHandler handler = map.get(method);

        if (handler == null) {
            throw new NullPointerException("Unable to find handler for method " + method);
        }

        return handler.invoke(proxy, method, args);
    }

    private boolean smartEquals(Object o) {

        if (o == null) {
            return false;
        }

        if (proxyClass.isAssignableFrom(o.getClass())) {
            // note: this has the possibility for false positives given hash collision, but the chance of that
            // is hopefully low enough that it won't caused issues in practice
            return hashCode() == o.hashCode();
        }

        return equals(o);
    }
}
