# Screech - Async HTTP, made easier 

[![Build Status](https://semaphoreci.com/api/v1/gryphon-zone/screech/branches/master/badge.svg)](https://semaphoreci.com/gryphon-zone/screech)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/zone.gryphon/screech-core/badge.png)](https://search.maven.org/artifact/zone.gryphon/screech-core/)

Screech is an HTTP abstraction library inspired by projects like 
[Retrofit](https://square.github.io/retrofit/) and [Feign](https://github.com/OpenFeign/feign), but with a focus
on support for [Asynchronous IO](https://en.wikipedia.org/wiki/Asynchronous_I/O).

## Benefits
Unlike traditional blocking IO, asynchronous (or non-blocking) IO doesn't require tying up a thread waiting on a response.
This means that a single thread can issue multiple requests concurrently, and handle the results as they come in.

In pseudo code, traditional blocking IO looks like the following:
```
// calling thread blocks until request completes
handleResponse(makeRequest());

// second request isn't started until first request is completed
handleResponse(makeAnotherRequest());
```

whereas non-blocking IO looks like the following:
```
// calling thread returns immediately, callback will be run when request completes 
makeRequest().whenComplete(response -> handleResponse(response));

// second request is issued before the first request completes
makeAnotherRequest().whenComplete(response -> handleResponse(response));
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
        return new ScreechBuilder(new JettyScreechClient())
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

## Java Version Compatibility

Screech makes heavy usage of Java 8 features, and is thus incompatible with Java 7 and below

## Adding to your project

To ease integration into projects using Maven, Screech publishes a Bill Of Materials (BOM) POM file, which can be used instead of manually managing dependency versions for individual modules.

To use it, add the following to your project's POM file (if you already have a dependency management section, add the dependency to the existing section):
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>zone.gryphon</groupId>
            <artifactId>screech-bom</artifactId>
            <version>${screech.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
``` 
where `screech.version` is defined in your project's `properties` section:
```xml
<properties>
    <screech.version>LATEST RELEASE</screech.version>
</properties>
```
You can visit the [releases](https://github.com/gryphon-zone/screech/releases) page to find the latest release version.

Once that's added, you can add the individual screech modules to your project without needing to re-define the versions, like so:
```xml
<dependencies>
    <dependency>
        <groupId>zone.gryphon</groupId>
        <artifactId>screech-annotations</artifactId>
    </dependency>
    <dependency>
        <groupId>zone.gryphon</groupId>
        <artifactId>screech-core</artifactId>
    </dependency>
</dependencies>
```

## Integrations 

Screech is modular, and allows all of the components that do the "heavy lifting" to be pluggable.
This includes the request serializer and deserializer, the underlying HTTP client, and any number of request interceptors you may want to add.

Screech plays well with other open source projects, and comes with out-of-the-box support for some of the most popular ones.

If you don't see your favorite library, adding support should be easy; most integrations are less than 100 lines of code.
If you write an integration and feel it should be included in the "standard" modules, feel free to open a pull request and explain why!

## Included Integrations

### Request encoding/decoding

#### Gson

JSON request encoding/decoding using Google's [gson](https://github.com/google/gson)

#### Jackson 2

JSON request encoding/decoding using [Jackson 2](https://github.com/FasterXML/jackson)

### HTTP clients

#### Async HTTP client

HTTP client utilizing [async-http-client](https://github.com/AsyncHttpClient/async-http-client)

#### Jetty HTTP client

HTTP client utilizing the [Jetty HTTP client](https://www.eclipse.org/jetty/documentation/9.4.x/http-client.html)

