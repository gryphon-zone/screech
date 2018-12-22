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

package zone.gryphon.screech.benchmark.tests;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Measurement(iterations = 10, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1)
@Warmup(iterations = 5, time = 1)
@Threads(1)
public class ScreechBenchmark {

    @Benchmark
    public void noop_methood(SimpleBenchmarkState state) {
        // no-op to get a baseline for what runtimes look like
    }

    @Benchmark
    public double generate_random_double(SimpleBenchmarkState state) {
        // simple method call to get baseline for runtimes
        return Math.random();
    }

    @Benchmark
    public Object run_empty_async_task(SimpleBenchmarkState state) throws Exception {
        // simple async method call to get a baseline for runtimes
        return state.service.submit(() -> null).get();
    }

    @Benchmark
    public Object GET_no_params(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.getMethodWithNoParams().get();
    }

    @Benchmark
    public Object GET_single_param(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.getMethodWithParam("foo").get();
    }

    @Benchmark
    public Object GET_multiple_params(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.getMethodWithMultipleParams("foo", "bar", "baz").get();
    }

    @Benchmark
    public Object GET_single_header(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.getMethodWithSingleHeader("foo").get();
    }

    @Benchmark
    public Object GET_multiple_headers(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.getMethodWithMultipleHeaders("foo", "bar", "baz").get();
    }

    @Benchmark
    public Object GET_multiple_headers_multiple_params(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.getMethodWithMultipleHeadersAndMultipleParams("foo", "bar", "baz").get();
    }


    @Benchmark
    public Object POST_no_params(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.postMethodWithNoParams("body").get();
    }

    @Benchmark
    public Object POST_single_param(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.postMethodWithParam("foo", "body").get();
    }

    @Benchmark
    public Object POST_multiple_params(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.postMethodWithMultipleParams("foo", "bar", "baz", "body").get();
    }

    @Benchmark
    public Object POST_single_header(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.postMethodWithSingleHeader("foo", "body").get();
    }

    @Benchmark
    public Object POST_multiple_headers(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.postMethodWithMultipleHeaders("foo", "bar", "baz", "body").get();
    }

    @Benchmark
    public Object POST_multiple_headers_multiple_params(SimpleBenchmarkState state) throws Exception {
        return state.screechTestInterface.postMethodWithMultipleHeadersAndMultipleParams("foo", "bar", "baz", "body").get();
    }

}
