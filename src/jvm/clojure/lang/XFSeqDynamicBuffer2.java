package clojure.lang;

public class XFSeqDynamicBuffer2 extends AFn {

    private static final int MIN_SIZE = 8;

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
            // Grows quickly to 32, then slows down.
            // 2 * 4 * 4
            int growth = idx <= 8 ? idx * 4 : idx * 2;
            Object[] larger = new Object[idx * growth];
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

    // Implements a reducing function (arities: 0, 1, 2)
    public Object invoke() {
        return new XFSeqDynamicBuffer2();
    }

    public Object invoke(Object a) {
        return a;
    }

    public Object invoke(Object a, Object b) {
        // assert(a == this);
        return this.conj(b);
    }
}
