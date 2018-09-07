package zone.gryphon.screech;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

    interface Expander {

        <T> String expand(T input);

    }

    class ToStringExpander implements Expander {

        @Override
        public <T> String expand(T input) {
            return Objects.toString(input, null);
        }
    }

    String value();

    Class<? extends Expander> expander() default ToStringExpander.class;
}
