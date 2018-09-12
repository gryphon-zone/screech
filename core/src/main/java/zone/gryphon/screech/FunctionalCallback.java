package zone.gryphon.screech;

public interface FunctionalCallback<T> extends Callback<T> {

    default void onSuccess(T result) {
        onComplete(result, null);
    }

    default void onError(Throwable e) {
        onComplete(null, e);
    }

    void onComplete(T result, Throwable e);

}
