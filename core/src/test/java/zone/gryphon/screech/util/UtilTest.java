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

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import zone.gryphon.screech.util.Util;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(Parameterized.class)
public class UtilTest {

    @Value
    @RequiredArgsConstructor
    public static class Tuple {

        private final Method method;

        private final String value;

    }

    public interface TestGenericInterface {

        void foo(String string);

        void bar(List<String> string);

        void baz(Map<String, String> string);

        void bibbly(Set<String> set, Map<String, Object> map);

    }

    @Parameterized.Parameters
    public static List<Tuple> parameters() throws Exception {
        return Arrays.asList(
                new Tuple(TestGenericInterface.class.getDeclaredMethod("foo", String.class), "TestGenericInterface.foo(String)"),
                new Tuple(TestGenericInterface.class.getDeclaredMethod("bar", List.class), "TestGenericInterface.bar(List<String>)"),
                new Tuple(TestGenericInterface.class.getDeclaredMethod("baz", Map.class), "TestGenericInterface.baz(Map<String, String>)"),
                new Tuple(TestGenericInterface.class.getDeclaredMethod("bibbly", Set.class, Map.class), "TestGenericInterface.bibbly(Set<String>, Map<String, Object>)")
        );
    }

    @Parameterized.Parameter
    public Tuple value;

    @Test
    public void name() {
        assertThat(Util.toString(value.getMethod())).isEqualTo(value.getValue());
    }


}