package zone.gryphon.screech;

import java.util.function.Consumer;

public interface Client {

    void request(SerializedRequest request, Consumer<SerializedResponse> callback);
}
