package clojure.lang;

import java.util.WeakHashMap;

/**
 * Seq which one can subscribe to. Subscribers can redirect the internal seq
 * when it is the only usage of the seq, to allow for faster flow through the collection.
 *
 * Subscriptions can only occur before the first call to .seq of the instance of this class.
 * This empowers the user to avoid any overhead of keeping track of the redirection logic.
 */
public class MedusaSeq implements Seqable, IRedirectableSeq {

    // TODO: Implement ISeq, to have (seq? (map inc ..)) => true (avoid breaking change).

    /**
     * Map from SeqHead -> ?
     */
    private WeakHashMap<Object, Boolean> refs;
    private Object seqable;
    private ISeq ret;

    public MedusaSeq(Object seqable) {
        this.seqable = seqable;
    }

    private ISeq internalSeq() {
        if (!(seqable instanceof ISeq)) {
            seqable = RT.seq(seqable);
        }
        return (ISeq)seqable;
    }

    private static ISeq wrappedSeq(final WeakHashMap<Object, Boolean> refs, final Object ref, final ISeq s2, final IFn rf) {
        IFn fn = new AFn() {
            @Override
            public Object invoke() {
                int refCount;
                synchronized (refs) {
                    // Call to .size will expunge all refs which have GC'ed.
                    refCount = refs.size();
                }
                System.err.println("Ref count: " + refCount);

                ISeq ret;
                switch(refCount) {
                    case 0:
                        throw new IllegalStateException("Ref count should never be 0 when creating a new element.");
                    case 1:
                        SeqRedirect redirect;
                        if (rf != null
                                && s2 instanceof ISeqRedirect
                                && (redirect = ((ISeqRedirect) s2).internalRedirect(rf)) != null) {
                            // TODO: redirect does not implement seqable, ISeq, or anything like that.
                            // TODO: making it problematic for LazySeq to handle right now.
                            ret = redirect;
                        } else {
                            ret = s2.seq();
                        }
                        break;
                    case 2:
                        ISeq s = s2.seq();
                        if (s == null) {
                            ret = null;
                        } else {
                            if (s instanceof IChunkedSeq) {
                                IChunkedSeq cs = (IChunkedSeq) s;
                                ret = new ChunkedCons(cs.chunkedFirst(), wrappedSeq(refs, this, cs.chunkedMore(), rf));
                            } else {
                                ret = new Cons(s.first(), wrappedSeq(refs, this, s.more(), rf));
                            }
                        }
                        break;
                    default:
                        ret = s2.seq();
                        break;
                }
                return ret;
            }
        };

        // Removes the previous ref and adds the fn on there.
        // When the fn is invoked, it'll call this method again
        // and remove itself, while adding on a new fn.
        synchronized (refs) {
            refs.remove(ref);
            refs.put(fn, true);
        }

        return new LazySeq(fn);
    }

    private void ensureRefsMap() {
        if (refs == null) {
            this.refs = new WeakHashMap<Object, Boolean>();
            // This acts as the "seq()'s" ref.
            refs.put(this, true);
        }
    }

    @Override
    public synchronized ISeq seq() {
        if (ret == null) {
            ISeq s = internalSeq();
            if (refs != null) {
                // Set refs to null, such that it's not hanging on to "this" (would it really?)
                WeakHashMap<Object, Boolean> rs = refs;
                refs = null;
                s = wrappedSeq(rs, this, s, null);
            }
            ret = s;
            // One can no longer subscribe to the seq
            seqable = null;
        }
        return ret;
    }

    // TODO: Add (Object acc) to this sub call?
    // TODO: That way, SubRedirect can drive the redirection, via either reduce or some XFSeq-like thing.
    @Override
    public synchronized Seqable sub(IFn rf) {
        // TODO: Only allow for one subscription? Then use the returned seq from seq()?
        // TODO: Check if seqable is non-nil? (Actually, check if it's takeoverable?)
        if (ret == null) {
            ensureRefsMap();
            return wrappedSeq(refs, null, internalSeq(), rf);
        } else {
            return ret;
        }
        // TODO: Somehow implement ISeqRedirect on LazySeq?
    }

    @Override
    protected void finalize() throws Throwable {
        System.err.println("MedusaSeq gc'ed");
        super.finalize();
    }
}
