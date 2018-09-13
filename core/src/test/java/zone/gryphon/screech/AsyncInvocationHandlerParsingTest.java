package zone.gryphon.screech;


import org.junit.Test;
import zone.gryphon.screech.model.HttpParam;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
public class AsyncInvocationHandlerParsingTest {

    private RequestEncoder requestEncoder = mock(RequestEncoder.class);

    private List<RequestInterceptor> requestInterceptors = Collections.emptyList();

    private ResponseDecoder responseDecoder = mock(ResponseDecoder.class);

    private ErrorDecoder errorDecoder = mock(ErrorDecoder.class);

    private Client client = mock(Client.class);

    private Target target = mock(Target.class);

    public interface NoAnnotation {

        @SuppressWarnings("unused")
        String foo();

    }

    @Test
    public void testNoAnnotation() {

        try {
            AsyncInvocationHandler.from(NoAnnotation.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalAccessException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("method is not annotated with RequestLine");
        }
    }

    public interface NoRequestLine {

        @SuppressWarnings("unused")
        @RequestLine("")
        String foo();

    }

    @Test
    public void testNoRequestLine() {

        try {
            AsyncInvocationHandler.from(NoRequestLine.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalAccessException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("no HTTP method defined");
        }
    }

    public interface NoPath {

        @SuppressWarnings("unused")
        @RequestLine("GET")
        String foo();
    }

    @Test
    public void testNoPath() {

        try {
            AsyncInvocationHandler.from(NoPath.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalAccessException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("no URL path defined");
        }
    }

    public interface NoVerb {

        @SuppressWarnings("unused")
        @RequestLine("/target")
        String foo();
    }

    @Test
    public void testNoVerb() {

        try {
            AsyncInvocationHandler.from(NoVerb.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalAccessException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("no HTTP method defined");
        }
    }

    public interface IllegalMethodHeader {

        @SuppressWarnings("unused")
        @Header("invalid header")
        @RequestLine("GET /target")
        String foo();
    }

    @Test
    public void testIllegalMethodHeader() {

        try {
            AsyncInvocationHandler.from(IllegalMethodHeader.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalAccessException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("Failed to parse valid header from value \"invalid header\" on method");
        }
    }

    @Header("invalid header")
    public interface IllegalClassHeader {

        @SuppressWarnings("unused")
        @RequestLine("GET /target")
        String foo();
    }

    @Test
    public void testIllegalClassHeader() {

        try {
            AsyncInvocationHandler.from(IllegalClassHeader.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalAccessException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("Failed to parse valid header from value \"invalid header\" on method");
        }
    }

    public interface MultipleBodyParams {

        @SuppressWarnings("unused")
        @RequestLine("GET /foo")
        String foo(String body1, String body2);

    }

    @Test
    public void testMultipleBodyParams() {

        try {
            AsyncInvocationHandler.from(MultipleBodyParams.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("cannot have more than one body param");
        }
    }

    public interface IllegalExpander {

        class InvalidExpander implements Param.Expander {

            private final String value;

            public InvalidExpander(String value) {
                this.value = value;
            }

            @Override
            public <T> String expand(T input) {
                return value;
            }
        }

        @SuppressWarnings("unused")
        @RequestLine("GET /foo")
        String foo(@Param(value = "foo", expander = InvalidExpander.class) String body);

    }

    @Test
    public void testIllegalExpander() {

        try {
            AsyncInvocationHandler.from(IllegalExpander.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).containsSubsequence("Failed to create expander");
        }
    }

    public interface SimpleClient {

        @RequestLine("GET /foo")
        String foo();

        @RequestLine("GET /bar?bar={bar}")
        String bar(@Param("bar") String bar);

        @Header("X-Header: {baz}")
        @RequestLine("GET /baz")
        String baz(@Param("baz") String baz);

        @RequestLine("POST /bibbly")
        String bibbly(String bibbly);
    }

    @Test
    public void testSimpleClient() throws Exception {

        AsyncInvocationHandler fooHandler = AsyncInvocationHandler.from(SimpleClient.class.getDeclaredMethod("foo"), requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(fooHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(fooHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(fooHandler.getPath()).isEqualTo("/foo");
        assertThat(fooHandler.getQueryParams()).isEmpty();
        assertThat(fooHandler.getHeaderParams()).isEmpty();


        AsyncInvocationHandler barHandler = AsyncInvocationHandler.from(SimpleClient.class.getDeclaredMethod("bar", String.class), requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(barHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(barHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(barHandler.getPath()).isEqualTo("/bar");
        assertThat(barHandler.getQueryParams()).containsExactly(new HttpParam("bar", "{bar}"));
        assertThat(barHandler.getHeaderParams()).isEmpty();

        AsyncInvocationHandler bazHandler = AsyncInvocationHandler.from(SimpleClient.class.getDeclaredMethod("baz", String.class), requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(bazHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(bazHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(bazHandler.getPath()).isEqualTo("/baz");
        assertThat(bazHandler.getQueryParams()).isEmpty();
        assertThat(bazHandler.getHeaderParams()).containsExactly(new HttpParam("X-Header", "{baz}"));

        AsyncInvocationHandler bibblyHandler = AsyncInvocationHandler.from(SimpleClient.class.getDeclaredMethod("bibbly", String.class), requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(bibblyHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(bibblyHandler.getHttpMethod()).isEqualTo("POST");
        assertThat(bibblyHandler.getPath()).isEqualTo("/bibbly");
        assertThat(bibblyHandler.getQueryParams()).isEmpty();
        assertThat(bibblyHandler.getHeaderParams()).isEmpty();
    }

    @Header("X-Header: header")
    public interface ClassLevelHeader {

        @SuppressWarnings("unused")
        @RequestLine("GET /foo")
        String foo();

    }

    @Test
    public void testClassLevelHeader() {

        AsyncInvocationHandler fooHandler = AsyncInvocationHandler.from(ClassLevelHeader.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(fooHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(fooHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(fooHandler.getPath()).isEqualTo("/foo");
        assertThat(fooHandler.getQueryParams()).isEmpty();
        assertThat(fooHandler.getHeaderParams()).containsExactly(new HttpParam("X-Header", "header"));
    }

    @Header("X-Header-1: header")
    public interface HeaderCombinations {

        @SuppressWarnings("unused")
        @Header("X-Header-2: header")
        @RequestLine("GET /foo")
        String foo();

    }

    @Test
    public void testHeaderCombinations() {

        AsyncInvocationHandler fooHandler = AsyncInvocationHandler.from(HeaderCombinations.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(fooHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(fooHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(fooHandler.getPath()).isEqualTo("/foo");
        assertThat(fooHandler.getQueryParams()).isEmpty();
        assertThat(fooHandler.getHeaderParams()).containsExactly(new HttpParam("X-Header-1", "header"), new HttpParam("X-Header-2", "header"));
    }

    public interface MultipleHeaders {

        @SuppressWarnings("unused")
        @Header("X-Header-1: header")
        @Header("X-Header-2: header")
        @RequestLine("GET /foo")
        String foo();

    }

    @Test
    public void testMultipleHeaders() {

        AsyncInvocationHandler fooHandler = AsyncInvocationHandler.from(MultipleHeaders.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(fooHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(fooHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(fooHandler.getPath()).isEqualTo("/foo");
        assertThat(fooHandler.getQueryParams()).isEmpty();
        assertThat(fooHandler.getHeaderParams()).containsExactly(new HttpParam("X-Header-1", "header"), new HttpParam("X-Header-2", "header"));
    }


    @Header("X-Header: header")
    public interface HeaderOverrides {

        @SuppressWarnings("unused")
        @Header("X-Header: overridden")
        @RequestLine("GET /foo")
        String foo();

    }

    @Test
    public void testHeaderOverrides() {

        AsyncInvocationHandler fooHandler = AsyncInvocationHandler.from(HeaderOverrides.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(fooHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(fooHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(fooHandler.getPath()).isEqualTo("/foo");
        assertThat(fooHandler.getQueryParams()).isEmpty();
        assertThat(fooHandler.getHeaderParams()).containsExactly(new HttpParam("X-Header", "overridden"));
    }

    @Header("X-Header-3: header")
    @Header("X-Header-4: header")
    public interface HeaderOrder {

        @SuppressWarnings("unused")
        @Header("X-Header-1: header")
        @Header("X-Header-2: header")
        @RequestLine("GET /foo")
        String foo();

    }

    @Test
    public void testHeaderOrder() {

        AsyncInvocationHandler fooHandler = AsyncInvocationHandler.from(HeaderOrder.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(fooHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(fooHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(fooHandler.getPath()).isEqualTo("/foo");
        assertThat(fooHandler.getQueryParams()).isEmpty();
        assertThat(fooHandler.getHeaderParams()).containsExactly(
                new HttpParam("X-Header-3", "header"),
                new HttpParam("X-Header-4", "header"),
                new HttpParam("X-Header-1", "header"),
                new HttpParam("X-Header-2", "header"));
    }

    public interface MultipleQueryParams {

        @SuppressWarnings("unused")
        @RequestLine("GET /foo?foo={foo}&bar=bar&baz=&bibbly&")
        String foo();

    }

    @Test
    public void testMultipleQueryParams() {

        AsyncInvocationHandler fooHandler = AsyncInvocationHandler.from(MultipleQueryParams.class.getDeclaredMethods()[0], requestEncoder, requestInterceptors, responseDecoder, errorDecoder, client, target);

        assertThat(fooHandler.getEffectiveReturnType()).isEqualTo(String.class);
        assertThat(fooHandler.getHttpMethod()).isEqualTo("GET");
        assertThat(fooHandler.getPath()).isEqualTo("/foo");
        assertThat(fooHandler.getQueryParams()).containsExactly(
                new HttpParam("foo", "{foo}"),
                new HttpParam("bar", "bar"),
                new HttpParam("baz", ""),
                new HttpParam("bibbly", null));
        assertThat(fooHandler.getHeaderParams()).isEmpty();
    }

}