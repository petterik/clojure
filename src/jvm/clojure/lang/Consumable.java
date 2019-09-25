package clojure.lang;

public interface Consumable {
    IReduceInit consumable(IFn xf);

    ISeq stack(IFn xf);
}
