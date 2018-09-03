package zone.gryphon.squawk;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class InstanceBuilder {

    private RequestEncoder requestEncoder = new RequestEncoder.StringRequestEncoder();

    private List<RequestInterceptor<?, ?, ?>> requestInterceptors = new ArrayList<>();

    private ResponseDecoder responseDecoder = new ResponseDecoder.StringResponseDecoder();

    private ErrorDecoder errorDecoder = new ErrorDecoder.DefaultErrorDecoder();

    private Client client;

    public InstanceBuilder(@NonNull Client client) {
        this.client = client;
    }

    public <T> T build(Class<T> clazz, Target target) {

        Map<Method, InvocationHandler> map = new HashMap<>();

        for (Method method : clazz.getMethods()) {

            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            map.put(method, AsyncInvocationHandler.from(method, requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target));
        }

        InvocationHandler handler = (proxy, method, args) -> map.get(method).invoke(proxy, method, args);

        //noinspection unchecked
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, handler);
    }

}
