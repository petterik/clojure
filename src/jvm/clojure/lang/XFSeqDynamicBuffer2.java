package clojure.lang;

public class XFSeqDynamicBuffer2 extends AFn {

    // Note: Changing this from 2, 4 and 8, sees huge difference
    //       in performance (java8).
    //       Setting it to 8 makes it faster on map inc and filter even
    //       compared to vanilla lazy-seqs
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
            // 8 * 4 * 2 * 2 * 2
            Object[] larger = new Object[idx * (idx <= 8 ? 4 : 2)];
            System.arraycopy(arr, 0, larger, 0, idx);
            arr = larger;
        }

        arr[idx++] = o;
        return this;
    }

    public ISeq toSeq(ISeq more) {
        ISeq s;
        switch(idx) {
            case 0:
                s = more;
                break;
            // TODO: Verify whether handrolling these arities is a good idea.
            case 1:
                s = new Cons(arr[0], more);
                idx = 0;
                arr[0] = null;
                break;
            case 2:
                s = new Cons(arr[0], new Cons(arr[1], more));
                idx = 0;
                arr[0] = null;
                arr[1] = null;
                break;
            case 3:
                s = new Cons(arr[0], new Cons(arr[1], new Cons(arr[2], more)));
                idx = 0;
                arr[0] = null;
                arr[1] = null;
                arr[2] = null;
                break;
            case 4:
                s = new Cons(arr[0], new Cons(arr[1], new Cons(arr[2], new Cons(arr[3], more))));
                idx = 0;
                arr[0] = null;
                arr[1] = null;
                arr[2] = null;
                arr[3] = null;
                break;
            default:
                s = new ChunkedCons(new ArrayChunk(arr, 0, idx), more);
                idx = 0;
                arr = null;
                break;
        }
        return s;
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
