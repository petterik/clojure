package clojure.lang;

// TODO: Something went wrong when refactoring from InlineStep.

public class XFSeqStep extends AFn {

    // Note: Changing this from 2, 4 and 8, sees huge difference
    //       in performance (java8).
    //       Setting it to 8 makes it faster on map inc and filter even
    //       compared to vanilla lazy-seqs
    private static final int MIN_SIZE = 8;
    private static final Object NOTHING = new Object();

    private Object item;

    private final IFn xf;
    private ISeq s;

    public XFSeqStep(IFn xf, ISeq s) {
        this.item = NOTHING;
        this.s = s;

        this.xf = (IFn)xf.invoke(this);
    }

    private ISeq invokeChunked(IChunkedSeq cs) {
        IChunk ch = cs.chunkedFirst();
        this.s = cs.chunkedMore();

        Object[] arr = new Object[ch.count()];
        int pos = 0;
        for (int i = 0; i < arr.length; i++) {
            if (this == xf.invoke(this, ch.nth(i))) {
                if (item != NOTHING) {
                    arr[pos++] = item;
                    item = NOTHING;
                }
            } else {
                if (item != NOTHING) {
                    arr[pos++] = item;
                    item = NOTHING;
                }
                this.s = PersistentList.EMPTY;
                break;
            }
        }
        if (pos == 0) {
            return new LazySeq(this);
        } else {
            return new ChunkedCons(new ArrayChunk(arr, 0, pos), new LazySeq(this));
        }
    }

    public Object invoke() {
        ISeq s = this.s.seq();
        if (s == null) {
            xf.invoke(this);
            return (item == NOTHING) ? null : new Cons(item, null);
        } else {
            if (s instanceof IChunkedSeq) {
                return invokeChunked((IChunkedSeq)s);
            } else {
                if (this == xf.invoke(this, s.first())) {
                    this.s = s.more();
                } else {
                    this.s = PersistentList.EMPTY;
                }

                if (item == NOTHING) {
                    return new LazySeq(this);
                } else {
                    ISeq ret = new Cons(item, new LazySeq(this));
                    item = NOTHING;
                    return ret;
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
        item = b;
        return a;
    }
}
