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
    private int idx;

    private final IFn xf;
    private ISeq s;

    public XFSeqStep(IFn xf, ISeq s) {
        this.idx = 0;
        this.item = NOTHING;
        this.s = s;

        this.xf = (IFn)xf.invoke(this);
    }

    public Object invoke() {
        ISeq ret;
        ISeq s = this.s.seq();
        if (s == null) {
            xf.invoke(this);
            if (item == NOTHING)
                ret = null;
            else {
                ret = new Cons(item, null);
            }
        } else {
            if (s instanceof IChunkedSeq) {
                IChunkedSeq cs = (IChunkedSeq)s;
                IChunk ch = cs.chunkedFirst();
                ISeq more = cs.chunkedMore();

                int size = ch.count();
                Object[] arr = new Object[size];
                int pos = 0;
                for (int i = 0; i < size; i++) {
                    Object invoked = xf.invoke(this, ch.nth(i));
                    if (item != NOTHING) {
                        arr[pos++] = item;
                        item = NOTHING;
                    }

                    // checking for reduced.
                    if (this != invoked) {
                        more = PersistentList.EMPTY;
                        break;
                    }
                }
                this.s = more;
                if (pos == 0) {
                    ret = new LazySeq(this);
                } else {
                    ret = new ChunkedCons(new ArrayChunk(arr, 0, pos), new LazySeq(this));
                }
            } else {
                ISeq more;
                if (this == xf.invoke(this, s.first())) {
                    more = s.more();
                } else {
                    more = PersistentList.EMPTY;
                }
                this.s = more;

                if (item == NOTHING) {
                    ret = new LazySeq(this);
                } else {
                    ret = new Cons(item, new LazySeq(this));
                    item = NOTHING;
                }
            }
        }
        return ret;
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
