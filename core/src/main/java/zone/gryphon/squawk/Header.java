package zone.gryphon.squawk;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Repeatable(Header.Headers.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface Header {

    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @interface Headers {

        Header[] value();

    }


}
