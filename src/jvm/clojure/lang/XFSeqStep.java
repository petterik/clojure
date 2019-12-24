package clojure.lang;

// TODO: Something went wrong when refactoring from InlineStep.

public class XFSeqStep extends AFn {

    // Note: Changing this from 2, 4 and 8, sees huge difference
    //       in performance (java8).
    //       Setting it to 8 makes it faster on map inc and filter even
    //       compared to vanilla lazy-seqs
    private static final int MIN_SIZE = 1;
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
        int len = ch.count();
        if (arr.length < len) {
            arr = new Object[len];
        }

        for (int i = 0; i < len; i++) {
            if (this != xf.invoke(this, ch.nth(i))) {
                return PersistentList.EMPTY;
            }
        }

        return cs.chunkedMore();
    }

    public Object invoke() {
        for(ISeq c = this.s.seq(); c != null; c = c.seq()) {
            if (c instanceof IChunkedSeq) {
                c = invokeChunked((IChunkedSeq) c);
            } else {
                if (this != xf.invoke(this, c.first())) {
                    break;
                }

                c = c.more();
            }

            if (idx != 0) {
                this.s = c;
                return toSeq(new LazySeq(this));
            }
        }

        xf.invoke(this);
        if (idx == 0) {
            return null;
        } else {
            return new ArrayCons(arr, 0, idx, null);
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

    private ISeq toSeq(ISeq seq) {
        switch(idx) {
            case 0:
                break;
            // TODO: Verify whether handrolling some cases is a good idea.
            case 1:
                seq = new Cons(arr[0], seq);
                idx = 0;
                break;
            case 2:
            case 3:
            case 4:
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
                seq = new ArrayCons(arrayCopy(), 0, idx, seq);
                // TODO: Do we need to clear this "cache/buffer" for GC purposes?
                System.arraycopy(NULLS, 0, arr, 0, idx);
                idx = 0;
                break;
            default:
                seq = chunkLargeResult(seq);
                break;
        }
        return seq;
    }

    private Object[] arrayCopy() {
        Object[] ret = new Object[idx];
        System.arraycopy(arr, 0, ret, 0, idx);
        return ret;
    }

    private ISeq chunkLargeResult(ISeq s) {
        // Returns 32 sized chunks in case the transduction created
        // more items than that. When chained, it can blow up.
        // This problem was found with code that repeated interpose:
        // (interpose nil (interpose nil ... (interpose nil (range)) ... ))
        int offset = idx;
        do {
            int end = offset;
            offset = Math.max(0, offset - 32);
            s = new ArrayCons(arr, offset, end, s);
        } while (offset > 0);
        arr = new Object[32];
        idx = 0;
        return s;
    }


    public static class ArrayCons extends ASeq implements IChunkedSeq, IChunk {

        private final Object[] array;
        private final int offset;
        private final int end;
        private final ISeq more;

        public ArrayCons(IPersistentMap meta, Object[] array, int offset, int end, ISeq more) {
            super(meta);
            this.array = array;
            this.offset = offset;
            this.end = end;
            this.more = more;
        }

        public ArrayCons(Object[] array, int offset, int end, ISeq more) {
            super(null);
            this.array = array;
            this.offset = offset;
            this.end = end;
            this.more = more;
        }

        @Override
        public IChunk chunkedFirst() {
            return this;
        }

        @Override
        public ISeq chunkedNext() {
            return chunkedMore().seq();
        }

        @Override
        public ISeq chunkedMore() {
            return more == null ? PersistentList.EMPTY : more;
        }

        @Override
        public Object first() {
            return array[offset];
        }

        @Override
        public ISeq next() {
            return more().seq();
        }

        @Override
        public ISeq more() {
            int next = offset + 1;
            if (next == end) {
                return more == null ? PersistentList.EMPTY : more;
            } else {
                return new ArrayCons(array, next, end, more);
            }
        }

        @Override
        public Obj withMeta(IPersistentMap meta){
            if(meta != _meta)
                return new ArrayCons(meta, array, offset, end, more);
            return this;
        }

        // IChunk

        @Override
        public IChunk dropFirst() {
            if(offset == end)
                throw new IllegalStateException("dropFirst of empty chunk");
            return new ArrayCons(array, offset + 1, end, more);
        }

        @Override
        public Object reduce(IFn f, Object start) {
            Object ret = f.invoke(start, array[offset]);
            if (RT.isReduced(ret))
                return ret;
            for (int i = offset + 1; i < end; i++) {
                ret = f.invoke(start, array[i]);
                if (RT.isReduced(ret)) {
                    return ret;
                }
            }
            return ret;
        }

        @Override
        public Object nth(int i) {
            return array[offset + i];
        }

        @Override
        public Object nth(int i, Object notFound) {
            if (i >= 0 && i < count()) {
                return array[offset + i];
            } else {
                return notFound;
            }
        }

        @Override
        public int count() {
            return end - offset;
        }
    }
}
