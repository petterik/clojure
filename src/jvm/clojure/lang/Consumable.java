package clojure.lang;

public interface Consumable {
    IReduceInit consumable();

    ISeq stack(IFn xf);
}
