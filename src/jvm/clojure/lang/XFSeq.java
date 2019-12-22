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
                XFSeqDynamicBuffer2 buf = new XFSeqDynamicBuffer2();
                IFn xform = (IFn)xf.invoke(buf);
                NextStep ns = new NextStep(xform, buf, (ISeq)s);
                s = ns.invoke();
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
            Object ret;
            do {
                ISeq c = s.seq();
                if (c == null) {
                    buf.scope();
                    xf.invoke(buf);
                    ret = buf.toSeq();
                } else {
                    if (c instanceof IChunkedSeq) {
                        c = invokeChunked((IChunkedSeq) c);
                    } else {
                        buf.scope();
                        if (buf == xf.invoke(buf, c.first())) {
                            c = c.more();
                        } else {
                            c = PersistentList.EMPTY;
                        }
                    }
                    s = c;
                    ret = buf.toSeq(this);
                }
            } while (ret == this);
            return ret;
        }

        public LazySeq toLazySeq() {
            return new LazySeq(this);
        }
    }

    public static ISeq create(IFn xform, Object coll) {
        return new LazySeq(new InitLazySeq(xform, coll));
    }

    public static ISeq createStackable(IFn xform, Object coll) {
        return new LazySeq(xform, coll, null);
    }
}
