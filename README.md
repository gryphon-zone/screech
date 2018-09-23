# Screech [![Build Status](https://semaphoreci.com/api/v1/gryphon-zone/screech/branches/master/badge.svg)](https://semaphoreci.com/gryphon-zone/screech)

Screech is an HTTP abstraction library inspired by projects like 
[Retrofit](https://square.github.io/retrofit/) and [Feign](https://github.com/OpenFeign/feign), but with a focus
on support for [Asynchronous IO](https://en.wikipedia.org/wiki/Asynchronous_I/O).

## Benefits
Unlike traditional blocking IO, asynchronous (or non-blocking) IO doesn't require tying up a thread waiting on a response.
This means that a single thread can issue multiple requests concurrently, and handle the results as they come in.

In pseudo code, traditional blocking IO looks like the following:
```
// calling thread blocks until request completes
response1 = makeRequest();
handleResponse(response1);

// second request isn't started until first request is completed
response2 = makeRequest();
handleResponse(response2);
```

whereas non-blocking IO looks like the following:
```
// calling thread returns immediately, callback will be run when request completes 
makeRequest().whenComplete(response -> handleResponse(response));

// second request is issued before the first request completes
makeRequest().whenComplete(response -> handleResponse(response));
```

This means that for applications which either issue a large number of requests, or that call endpoints which take a long
time to respond, the use of non-blocking IO can greatly reduce the number of threads required, and hence the memory
required by the application (as well as CPU required to context switch between threads).

## Example Usage

A typical Screech client implementation will look something like this:

```java
@Header("Accept: application/json")
public interface WidgetsClient {

    @RequestLine("GET /v1/widgets/{id}")
    CompletableFuture<Widget> getWidget(@Param("id") UUID id);

}
```

And can be instantiated like the following 
(note that while this example uses [Jackson](https://github.com/FasterXML/jackson-core) for request encoding/decoding
and [Jetty](https://www.eclipse.org/jetty/) as the underlying HTTP client, it's configurable based on what best suits your needs):
 
 ```java
public class Example {

    public static WidgetClient buildClient() {
        return new InstanceBuilder(new JettyScreechClient())
            .requestDecoder(new JacksonDecoder())
            .requestEncoder(new JacksonEncoder())
            .build(WidgetsClient.class, new HardCodedTarget("http://example.com"));
    }

}
 ```
 
Once instantiated, the client can be used like any other Java interface:

```java
public class ExampleUsage {
    
    public static void main(String... args) {
        WdigetsClient client = Example.buildClient();

        client.getWidget(UUID.randomUUID()).thenAccept(widget -> {
            System.out.println("I got a widget: " + widget);
        });
    }
}
```

