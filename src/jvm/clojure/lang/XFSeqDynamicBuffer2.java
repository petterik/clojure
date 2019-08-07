package clojure.lang;

public class XFSeqDynamicBuffer2 {

    private static final int MIN_SIZE = 8;
    private static final int GROWTH_MULTIPLIER = 2;

    private Object[] arr;
    private int idx;

    public XFSeqDynamicBuffer2(){
        idx = 0;
        arr = new Object[MIN_SIZE];
    }

    public XFSeqDynamicBuffer2 scope() {
        if (arr == null) {
            arr = new Object[idx < MIN_SIZE ? MIN_SIZE : idx];
        }
        return this;
    }

    public XFSeqDynamicBuffer2 scope(int size) {
        if (arr == null) {
            arr = new Object[size < MIN_SIZE ? MIN_SIZE : size];
        }
        return this;
    }

    public XFSeqDynamicBuffer2 conj(Object o) {
        if (idx == arr.length) {
            Object[] larger = new Object[idx * GROWTH_MULTIPLIER];
            System.arraycopy(arr, 0, larger, 0, idx);
            arr = larger;
        }

        arr[idx++] = o;
        return this;
    }

    public ISeq toSeq(ISeq more) {
        switch(idx) {
            case 0:
                return more;
            case 1:
                idx = 0;
                return new Cons(arr[0], more);
            case 2:
                // TODO: Verify whether handrolling these arities is a good idea.
                idx = 0;
                return new Cons(arr[0], new Cons(arr[1], more));
            case 3:
                idx = 0;
                return new Cons(arr[0], new Cons(arr[1], new Cons(arr[2], more)));
            default:
                Object[] chunk = arr;
                int end = idx;
                arr = new Object[end < MIN_SIZE ? MIN_SIZE : chunk.length];
                idx = 0;
                return new ChunkedCons(new ArrayChunk(chunk, 0, end), more);
        }
    }
}
