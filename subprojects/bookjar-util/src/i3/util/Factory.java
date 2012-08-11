package i3.util;

/**
 * A general typesafe factory
 * Only one create method argument for creation encapsulation.
 * Normally the argument is used to distinguish between
 * different implementations or RESULT. If you need aditional
 * arguments to make RESULT (not to distinguish them)
 * save them in the factory constructor. This is done so
 * api code only has to call the code with the diference factor
 * and everything else is encapsuled in the factory, making for
 * generic api code.
 *
 * All exceptions thrown by create should be caught by the
 * api that uses the factory implementation and translated
 * to a api compiletime exception.
 * @author Owner
 */
public abstract class Factory<RESULT, ARGUMENT> {

    /**
     * Create your results here,
     * return a null object or
     * throw an exception
     * if results cant be created.
     */
    public abstract RESULT create(ARGUMENT arg) throws Exception;
}
