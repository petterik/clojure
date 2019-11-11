package clojure.lang;

import java.util.WeakHashMap;

interface SubscribableSeq {
    /**
     *  A seq which can be "listened" and potentially "taken over".
     */
    ISeq sub(IFn rf);
}

/**
 * Seq which one can subscribe to. Subscribers can redirect the internal seq
 * when it is the only usage of the seq, to allow for faster flow through the collection.
 *
 * Subscriptions can only occur before the first call to .seq of the instance of this class.
 * This empowers the user to avoid any overhead of keeping track of the redirection logic.
 */
public class MedusaSeq implements Seqable, SubscribableSeq {

    // TODO: Implement ISeq, to have (seq? (map inc ..)) => true (avoid breaking change).

    public interface SeqRedirect {
        /**
         * Seq puts its items on rf instead of whatever it was doing.
         * Returns the head of the collection, which first/next is called
         * puts things onto rf.
         */
        ISeq internalRedirect(IFn rf);
    }

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
                ISeq s = s2.seq();
                synchronized (refs) {
                    // Call to .size will expunge all refs which have GC'ed.
                    if (refs.size() < 2) {
                        if (rf != null && s instanceof SeqRedirect) {
                            return ((SeqRedirect) s).internalRedirect(rf);
                        } else {
                            return s;
                        }
                    }
                }

                ISeq ls;
                if (s instanceof IChunkedSeq) {
                    IChunkedSeq cs = (IChunkedSeq) s;
                    ls = new ChunkedCons(cs.chunkedFirst(), wrappedSeq(refs, this, cs.chunkedMore(), rf));
                } else {
                    ls = new Cons(s.first(), wrappedSeq(refs, this, s.more(), rf));
                }
                return ls;
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

    @Override
    public synchronized ISeq sub(IFn rf) {
        // TODO: Only allow for one subscription? Then use the returned seq from seq()?
        // TODO: Check if seqable is non-nil? (Actually, check if it's takeoverable?)
        if (ret == null) {
            ensureRefsMap();
            return wrappedSeq(refs, null, internalSeq(), rf);
        } else {
            return ret;
        }
        // TODO: Somehow implement SeqRedirect on LazySeq?
    }
}
