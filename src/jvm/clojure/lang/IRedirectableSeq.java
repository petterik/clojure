package clojure.lang;

public interface IRedirectableSeq {

    /**
     *  A seq which can be "listened" and potentially "taken over".
     */
    Seqable sub(IFn rf);

}
