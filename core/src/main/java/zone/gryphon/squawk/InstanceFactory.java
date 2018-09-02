package zone.gryphon.squawk;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Slf4j
public class InstanceFactory {

    private Client client;

    public InstanceFactory(@NonNull Client client) {
        this.client = client;
    }

    public <T> T build(Class<T> clazz, Target target) {

        Map<Method, InvocationHandler> map = new HashMap<>();

        for (Method method : clazz.getMethods()) {

            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            map.put(method, AsyncInvocationHandler.from(method));
        }

        InvocationHandler handler = (proxy, method, args) -> map.get(method).invoke(proxy, method, args);

        //noinspection unchecked
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
    }

}
