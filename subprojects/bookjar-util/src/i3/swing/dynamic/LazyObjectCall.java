/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package i3.swing.dynamic;

import java.util.concurrent.Callable;

/**
 * interface used to mark your given objects in the Dynamic**** classes as late
 * bindings - method invoker will use the returned object(s) first to derive the
 * method (in the constructor) and then to actually run it.
 * These can be different objects, but must be of the same class (so the method matches)
 */
public interface LazyObjectCall extends Callable {

}
