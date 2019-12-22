package clojure.lang;

/**

 Based on the Clojure code:

 (def ^:static ^:const chunked-seq-class clojure.lang.IChunkedSeq)

 (defn ^:private ^:static xf-seq-step
   [^clojure.lang.ISeq s ^clojure.lang.IFn xf ^clojure.lang.XFSeqDynamicBuffer2 buf]
   (if (identical? s nil)
     (do
       (xf (.scope buf))
       (.toSeq buf nil))
     (let [s (if (.isInstance ^Class chunked-seq-class s)
       (let [ch (chunk-first ^clojure.lang.IChunkedSeq s)]
         (if (identical? buf (.reduce ch xf (.scope buf (.count ch))))
           (chunk-rest ^clojure.lang.IChunkedSeq s)
           ()))
         (if (identical? buf (xf buf (.first s)))
           (.more s)
           ()))]
       (.toSeq buf
         (lazy-seq
           (xf-seq-step (.seq ^clojure.lang.ISeq s) xf buf))))))

 (def ^:static xf-seq-arr-conj!
   (fn
     ([] (clojure.lang.XFSeqDynamicBuffer2.))
     ([buf] buf)
     ([buf x]
       (.conj ^clojure.lang.XFSeqDynamicBuffer2 buf x))))

 (def ^:static xf-seq
   (fn xf-seq [xform coll]
     (lazy-seq
       (let [s (seq coll)]
         (if s
           (xf-seq-step s (xform xf-seq-arr-conj!) (clojure.lang.XFSeqDynamicBuffer2.)))))))

 */
public class XFSeq {

    private static class InitLazySeq extends AFn {
        private final IFn xf;
        private final Object coll;

        InitLazySeq(IFn xf, Object coll) {
            this.xf = xf;
            this.coll = coll;
        }

        @Override
        public Object invoke() {
            Object s = RT.seq(coll);
            if (s != null) {
                s = new InlineNextStep(xf, (ISeq)s).invoke();
            }
            return s;
        }
    }

    public static class NextStep extends AFn {

        private final IFn xf;
        private final XFSeqDynamicBuffer2 buf;
        private ISeq s;

        NextStep(IFn xf, XFSeqDynamicBuffer2 buf, ISeq s) {
            this.xf = xf;
            this.buf = buf;
            this.s = s;
        }

        private ISeq invokeChunked(IChunkedSeq cs) {
            IChunk ch = cs.chunkedFirst();
            buf.scope(ch.count());
            if (buf == ch.reduce(xf, buf)) {
                return cs.chunkedMore();
            } else {
                return PersistentList.EMPTY;
            }
        }

        public Object invoke() {
            ISeq c = s.seq();
            if (c == null) {
                buf.scope();
                xf.invoke(buf);
                return buf.toSeq();
            } else {
                if (c instanceof IChunkedSeq) {
                    c = invokeChunked((IChunkedSeq) c);
                } else {
                    if (buf == xf.invoke(buf, c.first())) {
                        c = c.more();
                    } else {
                        c = PersistentList.EMPTY;
                    }
                }
                s = c;
                return buf.toSeq(new LazySeq(this));
            }
        }
    }

    public static ISeq create(IFn xform, Object coll) {
        return new LazySeq(new InitLazySeq(xform, coll));
    }

    public static ISeq createStackable(IFn xform, Object coll) {
        return new LazySeq(xform, coll, null);
    }

    /**
     * Inline NextStep buffer
     */
    public static class InlineNextStep extends AFn {

        // Note: Changing this from 2, 4 and 8, sees huge difference
        //       in performance (java8).
        //       Setting it to 8 makes it faster on map inc and filter even
        //       compared to vanilla lazy-seqs
        private static final int MIN_SIZE = 8;

        private Object[] arr;
        private int idx;

        private final IFn xf;
        private final AFn rf;
        private ISeq s;

        InlineNextStep(IFn xf, ISeq s) {
            this.idx = 0;
            this.arr = new Object[MIN_SIZE];
            this.s = s;

            this.rf = new AFn() {
                @Override
                public Object invoke() {
                    return this;
                }

                @Override
                public Object invoke(Object arg1) {
                    return this;
                }

                @Override
                public Object invoke(Object a, Object b) {
                    if (idx == arr.length) {
                        // Grows quickly to 32, then slows down.
                        // 8 * 4 * 2 * 2 * 2
                        Object[] larger = new Object[idx * (idx <= 8 ? 4 : 2)];
                        System.arraycopy(arr, 0, larger, 0, idx);
                        arr = larger;
                    }

                    arr[idx++] = b;
                    return this;
                }
            };
            this.xf = (IFn)xf.invoke(rf);
        }

        private ISeq invokeChunked(IChunkedSeq cs) {
            IChunk ch = cs.chunkedFirst();
            if (arr == null) {
                int size = ch.count();
                arr = new Object[size < MIN_SIZE ? MIN_SIZE : size];
            }
            if (rf == ch.reduce(xf, rf)) {
                return cs.chunkedMore();
            } else {
                return PersistentList.EMPTY;
            }
        }

        public Object invoke() {
            ISeq c = s.seq();
            if (c == null) {
                if (arr == null) {
                    arr = new Object[idx < MIN_SIZE ? MIN_SIZE : idx];
                }
                xf.invoke(rf);
                if (idx == 0) {
                    return null;
                } else {
                    // Doesn't need to set arr to nil as this is the final step in XFSeq.NextStep
                    return new ChunkedCons(new ArrayChunk(arr, 0, idx), null);
                }
            } else {
                if (c instanceof IChunkedSeq) {
                    c = invokeChunked((IChunkedSeq) c);
                } else {
                    if (arr == null) {
                        arr = new Object[idx < MIN_SIZE ? MIN_SIZE : idx];
                    }
                    if (rf == xf.invoke(rf, c.first())) {
                        c = c.more();
                    } else {
                        c = PersistentList.EMPTY;
                    }
                }
                s = c;
                return toSeq(new LazySeq(this));
            }
        }

        private Object toSeq(ISeq more) {
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
                    s = new ChunkedCons(new ArrayChunk(arr, 0, idx), more);
                    arr = null;
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
                    idx = 0;
                    arr = null;
                    break;
            }
            return s;
        }
    }

}
