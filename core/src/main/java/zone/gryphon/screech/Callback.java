package zone.gryphon.screech;

public interface Callback<T> {

    void onSuccess(T result);

    void onError(Throwable e);

}
