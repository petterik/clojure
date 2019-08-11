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
        private IFn xf;
        private Object coll;

        InitLazySeq(IFn xf, Object coll) {
            this.xf = xf;
            this.coll = coll;
        }

        @Override
        public Object invoke() {
            ISeq s = RT.seq(coll);
            if (s != null) {
                XFSeqDynamicBuffer2 buf = new XFSeqDynamicBuffer2();
                IFn xform = (IFn)xf.invoke(buf);
                xf = null;
                coll = null;
                s = step(s, xform, buf);
            }
            xf = null;
            coll = null;
            return s;
        };
    }

    private static class NextStep extends AFn {

        private ISeq s;
        private IFn xf;
        private XFSeqDynamicBuffer2 buf;

        NextStep(ISeq s, IFn xf, XFSeqDynamicBuffer2 buf) {
            this.s = s;
            this.xf = xf;
            this.buf = buf;
        }

        public Object invoke() {
            ISeq c = s.seq();
            IFn x = xf;
            XFSeqDynamicBuffer2 b = buf;
            s = null;
            xf = null;
            buf = null;
            return step(c, x, b);
        }
    }

    private static ISeq step(ISeq s, IFn xf, XFSeqDynamicBuffer2 buf) {
        ISeq ret;
        if (s == null) {
            xf.invoke(buf.scope());
            ret = buf.toSeq(null);
        } else {
            ISeq s2;
            if (s instanceof IChunkedSeq) {
                IChunk ch = ((IChunkedSeq)s).chunkedFirst();
                if (buf == ch.reduce(xf, (buf.scope(ch.count())))) {
                    s2 = ((IChunkedSeq)s).chunkedMore();
                } else {
                    s2 = PersistentList.EMPTY;
                }
            } else {
                if (buf == xf.invoke(buf, s.first())) {
                    s2 = s.more();
                } else {
                    s2 = PersistentList.EMPTY;
                }
            }
            ret = buf.toSeq(new LazySeq(new NextStep(s2, xf, buf)));
        }
        return ret;
    }

    public static ISeq create(IFn xform, Object coll) {
        return new LazySeq(new InitLazySeq(xform, coll));
    }
}
