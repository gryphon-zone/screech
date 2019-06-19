/*
 * Copyright 2019-2019 Gryphon Zone
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
 */

package zone.gryphon.screech.benchmark.tests;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import zone.gryphon.screech.ScreechBuilder;
import zone.gryphon.screech.benchmark.testing.NoOpClient;
import zone.gryphon.screech.benchmark.testing.ScreechTestInterface;
import zone.gryphon.screech.util.HardCodedTarget;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@State(Scope.Thread)
public class SimpleBenchmarkState {

    public final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public final ScreechTestInterface screechTestInterface;

    public SimpleBenchmarkState() {
        screechTestInterface = new ScreechBuilder(new NoOpClient())
                .requestExecutor(service)
                .responseExecutor(service)
                .build(ScreechTestInterface.class, new HardCodedTarget("http://localhost:8080"));
    }

    @TearDown
    public void cleanup() {
        service.shutdownNow();
    }

}
