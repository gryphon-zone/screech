package zone.gryphon.squawk;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class Response<T> {

    private final T entity;

}
