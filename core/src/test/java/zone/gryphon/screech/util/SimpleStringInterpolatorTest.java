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

package zone.gryphon.screech.util;


import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

@Slf4j
public class SimpleStringInterpolatorTest {

    private SimpleStringInterpolator create(String input) {
        return new SimpleStringInterpolator(input);
    }

    @Test
    public void testNoInterpolation() {
        final StringInterpolator test = create("foo");
        assertThat(test.interpolate(Collections.emptyMap())).isEqualTo("foo");
    }

    @Test
    public void testSimpleInterpolation() {
        final SimpleStringInterpolator test = create("{foo}");
        assertThat(test.interpolate(Collections.singletonMap("foo", "bar"))).isEqualTo("bar");
        assertThat(test.getConstants()).isEqualTo(Collections.singletonList(null));
        assertThat(test.getParameterNames()).isEqualTo(Collections.singletonList("foo"));
    }

    @Test
    public void testWithLeadingSpaceInterpolation() {
        final SimpleStringInterpolator test = create(" {foo}");
        assertThat(test.interpolate(Collections.singletonMap("foo", "bar"))).isEqualTo(" bar");
        assertThat(test.getConstants()).isEqualTo(Arrays.asList(" ", null));
        assertThat(test.getParameterNames()).isEqualTo(Collections.singletonList("foo"));
    }

    @Test
    public void testWithTrailingSpaceInterpolation() {
        final SimpleStringInterpolator test = create("{foo} ");
        assertThat(test.interpolate(Collections.singletonMap("foo", "bar"))).isEqualTo("bar ");
        assertThat(test.getConstants()).isEqualTo(Arrays.asList(null, " "));
        assertThat(test.getParameterNames()).isEqualTo(Collections.singletonList("foo"));
    }

    @Test
    public void testMultipleInterpolation() {
        final SimpleStringInterpolator test = create("{foo}");
        assertThat(test.interpolate(Collections.singletonMap("foo", "bar"))).isEqualTo("bar");
        assertThat(test.interpolate(Collections.singletonMap("foo", "baz"))).isEqualTo("baz");
        assertThat(test.getConstants()).isEqualTo(Collections.singletonList(null));
        assertThat(test.getParameterNames()).isEqualTo(Collections.singletonList("foo"));
    }

    @Test
    public void testMultipleInterpolationParams() {
        final SimpleStringInterpolator test = create("one={one}, two={two}, three={three}");
        assertThat(test.interpolate(of("one", "1", "two", "2", "three", "3"))).isEqualTo("one=1, two=2, three=3");
        assertThat(test.getConstants()).isEqualTo(Arrays.asList("one=", null, ", two=", null, ", three=", null));
        assertThat(test.getParameterNames()).isEqualTo(Arrays.asList("one", "two", "three"));
    }

    @Test
    public void testNullInput() {
        try {
            create(null);
            failBecauseExceptionWasNotThrown(NullPointerException.class);
        } catch (NullPointerException e) {
            assertThat(e.getMessage()).contains("input");
        }
    }

    @Test
    public void testMissingValueFromMap() {
        final StringInterpolator test = create("{foo}");

        try {
            test.interpolate(Collections.emptyMap());
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Unable to interpolate \"{foo}\", no value for key \"foo\"");
        }

        try {
            test.interpolate(Collections.singletonMap("bar", "foo"));
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Unable to interpolate \"{foo}\", no value for key \"foo\"");
        }
    }

    @Test
    public void testInvalidInputStrings() {

        try {
            create("foo {{}");
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Found '{' character without matching '}' character in string 'foo {{}'");
        }

        try {
            create("{foo {}");
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Found '{' character without matching '}' character in string '{foo {}'");
        }

        try {
            create("{foo{}}");
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Found '{' character without matching '}' character in string '{foo{}}'");
        }

        try {
            create("{{foo}}");
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Found '{' character without matching '}' character in string '{{foo}}'");
        }

        try {
            create("foo }");
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Found '}' character without matching '{' character in string 'foo }'");
        }

        try {
            create("foo {");
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("Found '{' character without matching '}' character in string 'foo {'");
        }
    }


    private Map<String, String> of(String... args) {

        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Must have even number of arguments");
        }

        Map<String, String> out = new HashMap<>();

        for (int i = 0; i < args.length; i += 2) {
            out.put(args[i], args[i + 1]);
        }

        return out;
    }
}