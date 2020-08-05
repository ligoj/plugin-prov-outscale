/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.outscale.catalog;

/**
 * An operation that accepts four input arguments and returns no result.
 *
 * @param <K> type of the first argument
 * @param <V> type of the second argument
 * @param <S> type of the third argument
 * @param <T> type of the fourth argument
 */
public interface QuadConsumer<K, V, S, T> {

	/**
	 * Performs the operation given the specified arguments.
	 * 
	 * @param k the first input argument
	 * @param v the second input argument
	 * @param s the third input argument
	 * @param t the fourth input argument
	 */
	void accept(K k, V v, S s, T t);
}
