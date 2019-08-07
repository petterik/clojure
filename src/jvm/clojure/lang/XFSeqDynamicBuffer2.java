package clojure.lang;

public class XFSeqDynamicBuffer2 {

    private Object[] arr;
    private int idx;

    public XFSeqDynamicBuffer2(){
        idx = 0;
    }

    public XFSeqDynamicBuffer2 scope() {
        if (arr == null) {
            arr = new Object[idx < 4 ? 4 : idx];
        }
        return this;
    }

    public XFSeqDynamicBuffer2 scope(int size) {
        if (arr == null) {
            arr = new Object[size < 4 ? 4 : size];
        }
        return this;
    }

    public XFSeqDynamicBuffer2 conj(Object o) {
        if (idx == arr.length) {
            Object[] larger = new Object[idx * 2];
            System.arraycopy(arr, 0, larger, 0, idx);
            arr = larger;
        }

        arr[idx++] = o;
        return this;
    }

    public ISeq toSeq(ISeq more) {
        switch(idx) {
            case 0:
                return more;
            case 1:
                idx = 0;
                return new Cons(arr[0], more);
            default:
                Object[] chunk = arr;
                int end = idx;
                arr = null;
                idx = 0;
                return new ChunkedCons(new ArrayChunk(chunk, 0, end), more);
        }
    }
}
