package clojure.lang;

public interface ISeqRedirect {

    SeqRedirect internalRedirect(IFn rf);

}
