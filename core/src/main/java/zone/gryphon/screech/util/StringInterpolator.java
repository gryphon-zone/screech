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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StringInterpolator implements StringInterpolatorApi {

    public static boolean requiresInterpolation(String input) {

        if (input == null) {
            return false;
        }

        return input.contains("{") || input.contains("}");
    }

    public static StringInterpolatorApi of(String input) {
        return requiresInterpolation(input) ? new StringInterpolator(input) : params -> input;
    }

    @Getter(AccessLevel.PACKAGE)
    private final List<String> constants;

    @Getter(AccessLevel.PACKAGE)
    private final List<String> parameterNames;

    private final int constantSize;
    private final String input;

    private StringInterpolator(@NonNull String input) {
        ArrayList<String> tempParamNames = new ArrayList<>();
        ArrayList<String> tempConstants = new ArrayList<>();

        calculateParams(input, tempConstants, tempParamNames);

        // reduce memory footprint, since we know all operations after this are read only
        tempConstants.trimToSize();
        tempParamNames.trimToSize();

        this.input = input;
        this.constants = Collections.unmodifiableList(tempConstants);
        this.parameterNames = Collections.unmodifiableList(tempParamNames);
        this.constantSize = this.constants.stream().filter(Objects::nonNull).mapToInt(String::length).sum();
    }

    private static void calculateParams(String input, List<String> constants, List<String> params) {
        boolean dirty = false;
        boolean inParameterName = false;
        int start = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '{') {
                if (inParameterName) {
                    throw new IllegalArgumentException(String.format("Found '{' character without matching '}' character in string '%s'", input));
                }

                if (dirty) {
                    constants.add(input.substring(start, i));
                    dirty = false;
                }

                inParameterName = true;
                start = i + 1;
            } else if (c == '}') {
                if (!inParameterName) {
                    throw new IllegalArgumentException(String.format("Found '}' character without matching '{' character in string '%s'", input));
                }

                inParameterName = false;

                constants.add(null);
                params.add(input.substring(start, i));
                dirty = false;
                start = i + 1;
            } else {
                dirty = true;
            }
        }

        if (inParameterName) {
            throw new IllegalArgumentException(String.format("Found '{' character without matching '}' character in string '%s'", input));
        }

        if (dirty) {
            constants.add(input.substring(start));
        }
    }


    public String interpolate(@NonNull Map<String, String> p) {
        StringBuilder out = new StringBuilder(constantSize + calculateParamLength(p));

        int paramIndex = 0;

        for (String constant : constants) {
            if (constant != null) {
                out.append(constant);
            } else {
                // calculateParamLength already ensured that there's a value for every parameter name
                out.append(p.get(parameterNames.get(paramIndex++)));
            }
        }

        return out.toString();
    }

    private int calculateParamLength(Map<String, String> p) {
        int size = 0;

        for (String paramName : parameterNames) {
            String value = p.get(paramName);

            if (value == null) {
                throw new IllegalArgumentException(String.format("Unable to interpolate \"%s\", no value for key \"%s\"", input, paramName));
            }

            size += value.length();
        }

        return size;
    }

}
