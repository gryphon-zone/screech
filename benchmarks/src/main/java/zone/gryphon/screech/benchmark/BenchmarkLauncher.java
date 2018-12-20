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

package zone.gryphon.screech.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;

public class BenchmarkLauncher {

    public static void main(String... args) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder().parent(new CommandLineOptions(args));

        Options initialOptions = builder.build();

        if (!initialOptions.getResultFormat().hasValue()) {
            builder.resultFormat(ResultFormatType.JSON);
        }

        if (!initialOptions.getResult().hasValue()) {
            File file = new File(System.getProperty("outputDir", "."), "benchmark_results.json").getAbsoluteFile();
            System.out.println("Writing results to " + file);
            builder.result(file.getAbsolutePath());
        }

        new Runner(builder.build()).run();
    }
}
