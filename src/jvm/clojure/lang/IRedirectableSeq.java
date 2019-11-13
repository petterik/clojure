package clojure.lang;

public interface IRedirectableSeq {

    /**
     *  A seq which can be "listened" and potentially "taken over".
     */
    ISeq sub(IFn rf);

}
