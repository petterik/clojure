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

    public ISeq toSeq() {
        ISeq ret;
        if (idx == 0) {
            ret = null;
        } else {
            ret = new ChunkedCons(new ArrayChunk(arr, 0, idx), null);
        }
        arr = null;
        return ret;
    }

    public Object toSeq(XFSeq.NextStep more) {
        Object s;
        switch(idx) {
            case 0:
                s = more;
                break;
            // TODO: Verify whether handrolling these arities is a good idea.
            case 1:
                s = new Cons(arr[0], more.toLazySeq());
                idx = 0;
                arr[0] = null;
                break;
            case 2:
                s = new Cons(arr[0], new Cons(arr[1], more.toLazySeq()));
                idx = 0;
                arr[0] = null;
                arr[1] = null;
                break;
            case 3:
                s = new Cons(arr[0], new Cons(arr[1], new Cons(arr[2], more.toLazySeq())));
                idx = 0;
                arr[0] = null;
                arr[1] = null;
                arr[2] = null;
                break;
            case 4:
                s = new Cons(arr[0], new Cons(arr[1], new Cons(arr[2], new Cons(arr[3], more.toLazySeq()))));
                idx = 0;
                arr[0] = null;
                arr[1] = null;
                arr[2] = null;
                arr[3] = null;
                break;
            default:
                // Returns 32 sized chunks in case the transduction created
                // more items than that. When chained, it can blow up.
                // This problem was found with code that repeated interpose:
                // (interpose nil (interpose nil ... (interpose nil (range)) ... ))
                s = more.toLazySeq();
                int offset = idx;
                do {
                    int end = offset;
                    offset = Math.max(0, offset - 32);
                    s = new ChunkedCons(new ArrayChunk(arr, offset, end), (ISeq)s);
                } while (offset > 0);
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
