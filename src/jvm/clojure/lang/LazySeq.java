/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* rich Jan 31, 2009 */

package clojure.lang;

import java.util.*;

public final class LazySeq extends Obj implements Consumable, ISeq, Sequential, List, IPending, IHashEq{

private static final Keyword CONSUMED_SEQ = Keyword.intern("clojure.lang.LazySeq", "CONSUMED_SEQ");
private static final Var STRICT_CONSUMABLE_SEQS = RT.var("clojure.lang.LazySeq","*strict-consumable-seqs*").setDynamic();

static {
	STRICT_CONSUMABLE_SEQS.doReset(true);
}

private static boolean isStrictlyConsumable(){
	return (boolean) STRICT_CONSUMABLE_SEQS.get();
}

private IFn fn;
private Object sv;
private ISeq s;
private ConsumableInternals ci;

public LazySeq(IFn fn){
	this.fn = fn;
}

public LazySeq(IFn xf, Object coll, Object ls, boolean stackable) {
	this.fn = null;
	this.sv = ls;
	this.ci = stackable ? new StackableInternals(xf, coll) : new ConsumableInternals(xf, coll);
}

private LazySeq(IPersistentMap meta, ISeq s){
	super(meta);
	this.fn = null;
	this.s = s;
}

public Obj withMeta(IPersistentMap meta){
	if(meta() == meta)
		return this;
	return new LazySeq(meta, seq());
}

public synchronized ISeq stack(IFn xform) {
	if (ci != null) {
		ISeq s = ci.stack(xform);
		if (s != null && isStrictlyConsumable()) {
			sv = null;
		}
		return s;
	} else {
		return null;
	}
}

public synchronized IReduceInit consumable() {
	if (ci != null) {
		IReduceInit ret = ci.consumable();
		if (ret != null && isStrictlyConsumable()) {
			// Remove the reference to the seq value when strictly
			// enforcing the sequence not being seqable again, to
			// help the gc.
			sv = null;
		}
		return ret;
	} else {
		return null;
	}
}

final synchronized Object sval(){
	if(fn != null) {
		sv = fn.invoke();
		fn = null;
	} else {
		// There was no function, it might be a consumable lazy seq.
		// Consumable seq's coll is either a coll or the CONSUMABLE_SEQ value.
		// i.e. not null.
		if (ci != null) {
			// Ensure the collection was not reduced and safely null out
			// the consumable parts of the sequence.
			ci.ensureNotReduced();
			ci = null;
		}
	}
	if(sv != null)
		return sv;
	return s;
}

final synchronized public ISeq seq(){
	sval();
	if(sv != null)
		{
		Object ls = sv;
		sv = null;
		while(ls instanceof LazySeq)
			{
			ls = ((LazySeq)ls).sval();
			}
		s = RT.seq(ls);
		}
	return s;
}

public int count(){
	int c = 0;
	for(ISeq s = seq(); s != null; s = s.next())
		++c;                                                                                
	return c;
}

public Object first(){
	seq();
	if(s == null)
		return null;
	return s.first();
}

public ISeq next(){
	seq();
	if(s == null)
		return null;
	return s.next();	
}

public ISeq more(){
	seq();
	if(s == null)
		return PersistentList.EMPTY;
	return s.more();
}

public ISeq cons(Object o){
	return RT.cons(o, seq());
}

public IPersistentCollection empty(){
	return PersistentList.EMPTY;
}

public boolean equiv(Object o){
	ISeq s = seq();
	if(s != null)
		return s.equiv(o);
	else
		return (o instanceof Sequential || o instanceof List) && RT.seq(o) == null;
}

public int hashCode(){
	ISeq s = seq();
	if(s == null)
		return 1;
	return Util.hash(s);
}

public int hasheq(){
	return Murmur3.hashOrdered(this);
}

public boolean equals(Object o){
	ISeq s = seq();
	if(s != null)
		return s.equals(o);
	else
		return (o instanceof Sequential || o instanceof List) && RT.seq(o) == null;
}


// java.util.Collection implementation

public Object[] toArray(){
	return RT.seqToArray(seq());
}

public boolean add(Object o){
	throw new UnsupportedOperationException();
}

public boolean remove(Object o){
	throw new UnsupportedOperationException();
}

public boolean addAll(Collection c){
	throw new UnsupportedOperationException();
}

public void clear(){
	throw new UnsupportedOperationException();
}

public boolean retainAll(Collection c){
	throw new UnsupportedOperationException();
}

public boolean removeAll(Collection c){
	throw new UnsupportedOperationException();
}

public boolean containsAll(Collection c){
	for(Object o : c)
		{
		if(!contains(o))
			return false;
		}
	return true;
}

public Object[] toArray(Object[] a){
    return RT.seqToPassedArray(seq(), a);
}

public int size(){
	return count();
}

public boolean isEmpty(){
	return seq() == null;
}

public boolean contains(Object o){
	for(ISeq s = seq(); s != null; s = s.next())
		{
		if(Util.equiv(s.first(), o))
			return true;
		}
	return false;
}

public Iterator iterator(){
	return new SeqIterator(this);
}

//////////// List stuff /////////////////
private List reify(){
	return new ArrayList(this);
}

public List subList(int fromIndex, int toIndex){
	return reify().subList(fromIndex, toIndex);
}

public Object set(int index, Object element){
	throw new UnsupportedOperationException();
}

public Object remove(int index){
	throw new UnsupportedOperationException();
}

public int indexOf(Object o){
	ISeq s = seq();
	for(int i = 0; s != null; s = s.next(), i++)
		{
		if(Util.equiv(s.first(), o))
			return i;
		}
	return -1;
}

public int lastIndexOf(Object o){
	return reify().lastIndexOf(o);
}

public ListIterator listIterator(){
	return reify().listIterator();
}

public ListIterator listIterator(int index){
	return reify().listIterator(index);
}

public Object get(int index){
	return RT.nth(this, index);
}

public void add(int index, Object element){
	throw new UnsupportedOperationException();
}

public boolean addAll(int index, Collection c){
	throw new UnsupportedOperationException();
}

synchronized public boolean isRealized(){
	return fn == null;
}

private static class ConsumableInternals implements Consumable {
	private IFn xf;
	private Object coll;

	ConsumableInternals(IFn xf, Object coll) {
		this.xf = xf;
		this.coll = coll;
	}

	void ensureNotReduced(){
		if (coll == CONSUMED_SEQ) {
			if (isStrictlyConsumable()) {
				throw new RuntimeException("LazySeq's internals were destroyed when used as a Consumable");
			} else {
				System.err.println("WARN: Reduced seq is being reused");
				new Exception().printStackTrace();
			}
		}
	}

	@Override
	public IReduceInit consumable() {
		if (xf != null) {
			// TODO - Implement clojure.lang.Eduction
			//        Also maybe punt on that?
			// Recursively call consumable on coll if possible.
			Object root = clojure.lang.RT.asConsumable(coll);
			IReduceInit consumable = new Eduction(xf, root);
			xf = null;
			coll = CONSUMED_SEQ;
			return consumable;
		} else {
			ensureNotReduced();
			return null;
		}
	}

	@Override
	public ISeq stack(IFn xf) {
		return null;
	}
}

private static class StackableInternals extends ConsumableInternals {

	StackableInternals(IFn xf, Object coll) {
		super(xf, coll);
	}

	@Override
	public ISeq stack(IFn xform) {
		// A LazySeq is stackable when there's an xform which
		// behaves the same whether it's composed iwth
		if (super.xf != null) {
			ISeq s = clojure.lang.RT.stackSeqs(xform, super.xf, super.coll);
			super.xf = null;
			super.coll = CONSUMED_SEQ;
			return s;
		} else {
			return null;
		}
	}
}

}
