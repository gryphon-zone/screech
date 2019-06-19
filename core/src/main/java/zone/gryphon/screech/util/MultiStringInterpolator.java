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

package zone.gryphon.screech.util;

import lombok.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MultiStringInterpolator {

    private final Map<String, StringInterpolator> map;

    public MultiStringInterpolator() {
        this(Collections.emptyList());
    }

    public MultiStringInterpolator(@NonNull Collection<String> precache) {
        Map<String, StringInterpolator> localMap = new HashMap<>(precache.size());
        precache.forEach(key -> localMap.put(key, SimpleStringInterpolator.of(key)));
        this.map = Collections.unmodifiableMap(localMap);
    }

    public String interpolate(String key, Map<String, String> params) {

        if (key == null) {
            return null;
        }

        return map.getOrDefault(key, SimpleStringInterpolator.of(key)).interpolate(params);
    }
}
