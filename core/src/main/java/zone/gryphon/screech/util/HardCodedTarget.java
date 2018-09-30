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

import zone.gryphon.screech.Target;

import java.net.URI;
import java.net.URL;
import java.util.Objects;

public class HardCodedTarget implements Target {

    private final String target;

    public HardCodedTarget(URL target) {
        this(Objects.requireNonNull(target, "target may not be null").toString());
    }

    public HardCodedTarget(String target) {
        this.target = Objects.requireNonNull(target, "target may not be null");
    }

    public HardCodedTarget(URI target) {
        this(Objects.requireNonNull(target, "target may not be null").toString());
    }

    @Override
    public String getTarget() {
        return target;
    }
}
