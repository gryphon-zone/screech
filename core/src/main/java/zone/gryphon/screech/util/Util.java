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

import lombok.experimental.UtilityClass;
import zone.gryphon.screech.Callback;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.Callable;

@UtilityClass
public final class Util {

    public static String toString(Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getDeclaringClass().getSimpleName());
        builder.append(".");
        builder.append(method.getName());
        builder.append("(");

        Parameter[] params = method.getParameters();
        for (int i = 0; i < method.getParameterCount(); i++) {

            builder.append(params[i].getType().getSimpleName());

            if (params[i].getParameterizedType() instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) params[i].getParameterizedType();
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

                builder.append('<');
                for (int j = 0; j < actualTypeArguments.length; j++) {

                    if (actualTypeArguments[j] instanceof Class) {
                        builder.append(((Class) actualTypeArguments[j]).getSimpleName());
                    } else {
                        builder.append(actualTypeArguments[j]);
                    }

                    if (j < actualTypeArguments.length - 1) {
                        builder.append(", ");
                    }
                }
                builder.append('>');

            }

            if (i < method.getParameterCount() - 1) {
                builder.append(", ");
            }

        }

        builder.append(")");

        return builder.toString();
    }

    public static void runDangerousCode(Callback<?> callback, Executable executable) {
        Objects.requireNonNull(callback, "Provided callback cannot be null");
        Objects.requireNonNull(executable, "Provided executable cannot be null");

        try {
            executable.execute();
        } catch (Throwable throwable) {
            callback.onFailure(throwable);
        }
    }

    public static <T> T runDangerousCode(Callback<?> callback, Callable<T> callable) {
        Objects.requireNonNull(callback, "Provided callback cannot be null");
        Objects.requireNonNull(callable, "Provided callable cannot be null");

        try {
            return callable.call();
        } catch (Throwable throwable) {
            callback.onFailure(throwable);
            return null;
        }
    }

}
