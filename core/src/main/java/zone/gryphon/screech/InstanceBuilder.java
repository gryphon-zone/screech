package zone.gryphon.screech;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class InstanceBuilder {

    private RequestEncoder requestEncoder = new RequestEncoder.StringRequestEncoder();

    private List<RequestInterceptor> requestInterceptors = new ArrayList<>();

    private ResponseDecoder responseDecoder = new ResponseDecoder.StringResponseDecoder();

    private ErrorDecoder errorDecoder = new ErrorDecoder.DefaultErrorDecoder();

    private Client client;

    public InstanceBuilder(@NonNull Client client) {
        this.client = client;
    }

    public InstanceBuilder requestEncoder(@NonNull RequestEncoder requestEncoder) {
        this.requestEncoder = requestEncoder;
        return this;
    }

    public InstanceBuilder addRequestInterceptor(@NonNull RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return this;
    }

    public InstanceBuilder addRequestInterceptors(@NonNull Iterable<RequestInterceptor> requestInterceptors) {
        requestInterceptors.forEach(this::addRequestInterceptor);
        return this;
    }

    public InstanceBuilder responseDecoder(@NonNull ResponseDecoder responseDecoder) {
        this.responseDecoder = responseDecoder;
        return this;
    }

    public InstanceBuilder errorDecoder(@NonNull ErrorDecoder errorDecoder) {
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
