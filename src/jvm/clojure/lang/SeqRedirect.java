package clojure.lang;

public class SeqRedirect implements ISeq{
    private final IFn redirectedXForm;
    private final ISeq coll;

    public SeqRedirect(IFn redirectedXForm, ISeq coll) {
        this.redirectedXForm = redirectedXForm;
        this.coll = coll;
    }

    public IFn getRedirectedXForm() {
        return redirectedXForm;
    }

    public ISeq getColl() {
        return coll;
    }

    @Override
    public Object first() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ISeq next() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ISeq more() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int count() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ISeq cons(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPersistentCollection empty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equiv(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ISeq seq() {
        return this;
    }
}
