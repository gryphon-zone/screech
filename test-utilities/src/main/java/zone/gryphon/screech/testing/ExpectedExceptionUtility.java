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

package zone.gryphon.screech.testing;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ExpectedExceptionUtility {


    public static String simpleMessageFor(Throwable t) {

        if (t == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        Throwable active = t;
        boolean first = true;

        while (active != null) {
            if (!first) {
                builder.append("\n    Caused by: ");
            }

            builder.append(active.getClass().getName()).append(": ").append(active.getMessage());

            first = false;
            active = active.getCause();
        }

        return builder.toString();
    }

}
