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

package zone.gryphon.screech.benchmark.testing;

import zone.gryphon.screech.Header;
import zone.gryphon.screech.Param;
import zone.gryphon.screech.RequestLine;

import java.util.concurrent.CompletableFuture;

@Header("Accept: application/json")
public interface ScreechTestInterface {

    @RequestLine("GET /foo")
    CompletableFuture<String> getMethodWithNoParams();

    @RequestLine("GET /foo?foo={bar}")
    CompletableFuture<String> getMethodWithParam(@Param("bar") String bar);

    @RequestLine("GET /foo?foo={foo}&bar={bar}&baz={baz}")
    CompletableFuture<String> getMethodWithMultipleParams(@Param("foo") String foo, @Param("bar") String bar, @Param("baz") String baz);

    @Header("X-Foo: {foo}")
    @RequestLine("GET /foo")
    CompletableFuture<String> getMethodWithSingleHeader(@Param("foo") String foo);

    @Header("X-Foo: {foo}")
    @Header("X-Bar: {bar}")
    @Header("X-Baz: {baz}")
    @RequestLine("GET /foo")
    CompletableFuture<String> getMethodWithMultipleHeaders(@Param("foo") String foo, @Param("bar") String bar, @Param("baz") String baz);

    @Header("X-Foo: {foo}")
    @Header("X-Bar: {bar}")
    @Header("X-Baz: {baz}")
    @RequestLine("GET /foo?foo={foo}&bar={bar}&baz={baz}")
    CompletableFuture<String> getMethodWithMultipleHeadersAndMultipleParams(@Param("foo") String foo, @Param("bar") String bar, @Param("baz") String baz);



    @RequestLine("POST /foo")
    CompletableFuture<String> postMethodWithNoParams(String body);

    @RequestLine("POST /foo?foo={bar}")
    CompletableFuture<String> postMethodWithParam(@Param("bar") String bar, String body);

    @RequestLine("POST /foo?foo={foo}&bar={bar}&baz={baz}")
    CompletableFuture<String> postMethodWithMultipleParams(@Param("foo") String foo, @Param("bar") String bar, @Param("baz") String baz, String body);

    @Header("X-Foo: {foo}")
    @RequestLine("POST /foo")
    CompletableFuture<String> postMethodWithSingleHeader(@Param("foo") String foo, String body);

    @Header("X-Foo: {foo}")
    @Header("X-Bar: {bar}")
    @Header("X-Baz: {baz}")
    @RequestLine("POST /foo")
    CompletableFuture<String> postMethodWithMultipleHeaders(@Param("foo") String foo, @Param("bar") String bar, @Param("baz") String baz, String body);

    @Header("X-Foo: {foo}")
    @Header("X-Bar: {bar}")
    @Header("X-Baz: {baz}")
    @RequestLine("POST /foo?foo={foo}&bar={bar}&baz={baz}")
    CompletableFuture<String> postMethodWithMultipleHeadersAndMultipleParams(@Param("foo") String foo, @Param("bar") String bar, @Param("baz") String baz, String body);

}
