package clojure.lang;

// TODO: Something went wrong when refactoring from InlineStep.

public class XFSeqStep extends AFn {

    // Note: Changing this from 2, 4 and 8, sees huge difference
    //       in performance (java8).
    //       Setting it to 8 makes it faster on map inc and filter even
    //       compared to vanilla lazy-seqs
    private static final int MIN_SIZE = 8;
    private static final Object[] NULLS = new Object[64];

    private Object[] arr;
    private int idx;

    private final IFn xf;
    private ISeq s;

    public XFSeqStep(IFn xf, ISeq s) {
        this.s = s;
        this.arr = new Object[MIN_SIZE];
        this.idx = 0;

        this.xf = (IFn)xf.invoke(this);
    }

    private ISeq invokeChunked(IChunkedSeq cs) {
        IChunk ch = cs.chunkedFirst();
        this.s = cs.chunkedMore();
        int len = ch.count();
        if (arr.length < len) {
            arr = new Object[len];
        }

        for (int i = 0; i < len; i++) {
            if (this != xf.invoke(this, ch.nth(i))) {
                this.s = PersistentList.EMPTY;
                break;
            }
        }

        return toSeq(new LazySeq(this));
    }

    public Object invoke() {
        ISeq s = this.s.seq();
        if (s == null) {
            xf.invoke(this);
            return (idx == 0) ? null : new Cons(arr[0], null);
        } else {
            if (s instanceof IChunkedSeq) {
                return invokeChunked((IChunkedSeq)s);
            } else {
                if (this == xf.invoke(this, s.first())) {
                    this.s = s.more();
                } else {
                    this.s = PersistentList.EMPTY;
                }

                if (idx == 0) {
                    return new LazySeq(this);
                } else {
                    idx = 0;
                    return new Cons(arr[0], new LazySeq(this));
                }
            }
        }
    }

    @Override
    public Object invoke(Object a) {
        return a;
    }

    @Override
    public Object invoke(Object a, Object b) {
        arr[idx++] = b;
        return a;
    }

    private ISeq toSeq(ISeq more) {
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
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
                Object[] ret = new Object[idx];
                System.arraycopy(arr, 0, ret, 0, idx);
                s = new ChunkedCons(new ArrayChunk(ret, 0, idx), more);
                // TODO: Do we need to clear this "cache/buffer" for GC purposes?
                // System.arraycopy(NULLS, 0, arr, 0, idx);
                idx = 0;
                break;
            default:
                // Returns 32 sized chunks in case the transduction created
                // more items than that. When chained, it can blow up.
                // This problem was found with code that repeated interpose:
                // (interpose nil (interpose nil ... (interpose nil (range)) ... ))
                s = more;
                int offset = idx;
                do {
                    int end = offset;
                    offset = Math.max(0, offset - 32);
                    s = new ChunkedCons(new ArrayChunk(arr, offset, end), s);
                } while (offset > 0);
                arr = new Object[32];
                idx = 0;
                break;
        }
        return s;
    }
}
