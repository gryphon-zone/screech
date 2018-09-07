package zone.gryphon.screech;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(Header.Headers.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface Header {

    String value();

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Headers {

        Header[] value();

    }


}
