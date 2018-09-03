package zone.gryphon.squawk;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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

}
