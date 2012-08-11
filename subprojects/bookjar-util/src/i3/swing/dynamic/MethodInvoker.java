package i3.swing.dynamic;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Slightly tricky:
 * if the actual given object is a LazyObjectCall, it late binds the actual target
 * from its call method; if not it uses the object itself.
 * The LazyObjectCall can return a different object eventually,
 * but it has to be of the same class
 */
class MethodInvoker {

    private Method m;
    private final Object factory;

    public MethodInvoker(Object factory, String method, Class... arguments){
        this.factory = factory;
        Object target;
        try {
            target = (factory instanceof LazyObjectCall) ? ((LazyObjectCall)factory).call() : factory;
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        Class c = target.getClass();
        if (!Modifier.isPublic(c.getModifiers())) {
            throw new AssertionError("Runtime class " + c.getCanonicalName() + " must be public for dynamic method invocation to work");
        }

        try {
            m = c.getMethod(method, arguments);
        } catch (NoSuchMethodException | SecurityException ex) {

            Method[] methods = target.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                Class[] types = methods[i].getParameterTypes();

                if (methods[i].getName().equals(method) && isCompatible(types, arguments)) {
                    m = methods[i];
                    break;
                }
            }

            if (m == null) {
                String msg = "Failed getting the method " + method + "(" + arguments(arguments) + ") from class " + target.getClass().getCanonicalName();
                throw new AssertionError(msg, ex);
            }
        }
    }

    static Class[] toClasses(Object... arguments) {
        Class[] types = new Class[arguments.length];
        int i = 0;
        for (Object a : arguments) {
            Objects.requireNonNull(a, "It's required not to pass null");
            types[i++] = a.getClass();
        }
        return types;
    }

    static Class[] toClassesAndPrefix(Class first, Object... arguments) {
        Class[] types = new Class[arguments.length + 1];
        types[0] = first;
        int i = 0;
        for (Object a : arguments) {
            Objects.requireNonNull(a, "It's required not to pass null");
            types[i++] = a.getClass();
        }
        return types;
    }

    private static String arguments(Class... arguments) {
        StringBuilder s = new StringBuilder();

        if (arguments.length != 0) {
            s.append(arguments[0].getSimpleName());
        }
        for (int i = 1; i < arguments.length; i++) {
            s.append(',').
                    append(' ').
                    append(arguments[i] == null ? "null" : arguments[i].getSimpleName());
        }
        return s.toString();
    }

    public Class methodReturnType() {
        return m.getReturnType();
    }

    public Object runMethod(Object... arguments) {
        try {
                return m.invoke((factory instanceof LazyObjectCall) ? ((LazyObjectCall)factory).call() : factory, arguments);
        } catch (Exception ex) {
            String msg = "No exception should propagate outside method handler " + m.getName() + "(" + arguments(m.getParameterTypes()) + ") in class " + factory.getClass().getCanonicalName();
            throw new AssertionError(msg, ex);
        }
    }

    private static boolean isCompatible(Class[] types, Class[] arguments) {
        if (arguments.length != types.length) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (!primitiveAssignableFrom(types[i], arguments[i]) && !types[i].isAssignableFrom(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param c1 possibly primitive, needs to be to return true
     * @param c2 certainly not primitive, but needs to be wrapper
     * @return
     */
    private static boolean primitiveAssignableFrom(Class c1, Class c2) {
        if (c1 == boolean.class) {
            return c2 == Boolean.class;
        } else if (c1 == int.class) {
            return c2 == Integer.class;
        } else if (c1 == double.class) {
            return c2 == Double.class;
        } else if (c1 == float.class) {
            return c2 == Float.class;
        } else if (c1 == long.class) {
            return c2 == Long.class;
        } else if (c1 == byte.class) {
            return c2 == Byte.class;
        } else if (c1 == char.class) {
            return c2 == Character.class;
        } else {
            return c2 == Short.class;
        }
    }
}
