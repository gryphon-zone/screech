package zone.gryphon.screech;

import zone.gryphon.screech.model.SerializedRequest;
import zone.gryphon.screech.model.SerializedResponse;

public interface Client {

    void request(SerializedRequest request, Callback<SerializedResponse> callback);
}
