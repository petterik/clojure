package clojure.lang;

public class Eduction implements Seqable, Sequential, IReduceInit {

    private static final Object EDUCTION_LOCK = new Object();
    private static IFn CLOJURE_EDUCTION = null;

    private static IFn clojureEduction() {
        if (CLOJURE_EDUCTION == null) {
            synchronized (EDUCTION_LOCK) {
                if (CLOJURE_EDUCTION == null) {
                    CLOJURE_EDUCTION = RT.CLOJURE_NS.findInternedVar(Symbol.intern("eduction"));
                }
            }
        }
        return CLOJURE_EDUCTION;
    }

    private final IFn xform;
    private final Object coll;

    public Eduction(IFn xform, Object coll) {
        this.xform = xform;
        this.coll = coll;
    }

    @Override
    public Object reduce(IFn rf, Object start) {
        IFn eduction = clojureEduction();
        if (eduction != null) {
            Object edu = eduction.invoke(xform, coll);
            return ((IReduceInit) edu).reduce(rf, start);
        } else {
            IFn xf = (IFn)xform.invoke(rf);
            if (coll instanceof IReduceInit) {
                Object ret = ((IReduceInit) coll).reduce(xf, start);
                return xf.invoke(ret);
            } else {
                // Seq reduce
                ISeq s = RT.seq(coll);
                Object ret = start;
                while (s != null) {
                    if (s instanceof IChunkedSeq) {
                        IChunkedSeq cs = (IChunkedSeq)s;
                        IChunk ch = cs.chunkedFirst();
                        ret = ch.reduce(xf, ret);
                        s = cs.chunkedMore();
                    } else {
                        ret = xf.invoke(ret, s.first());
                        s = s.more();

                    }
                    if (ret instanceof Reduced) {
                        ret = ((Reduced) ret).deref();
                        break;
                    } else {
                        s = s.seq();
                    }
                }
                return xf.invoke(ret);
            }
        }
    }

    @Override
    public ISeq seq() {
        return XFSeq.create(xform, coll);
    }
}
