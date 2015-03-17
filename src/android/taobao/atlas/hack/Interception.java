package android.taobao.atlas.hack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Interception {

    private static interface Intercepted {
    }

    public static abstract class InterceptionHandler<T> implements InvocationHandler {
        private T mDelegatee;

        public Object invoke(Object obj, Method method, Object[] objArr) throws Throwable {
            Object obj2 = null;
            try {
                obj2 = method.invoke(delegatee(), objArr);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e2) {
                e2.printStackTrace();
            } catch (InvocationTargetException e3) {
                throw e3.getTargetException();
            }
            return obj2;
        }

        protected T delegatee() {
            return this.mDelegatee;
        }

        void setDelegatee(T t) {
            this.mDelegatee = t;
        }
    }

    public static Object proxy(Object obj, Class cls, InterceptionHandler  interceptionHandler) throws IllegalArgumentException {
        if (obj instanceof Intercepted) {
            return obj;
        }
        interceptionHandler.setDelegatee(obj);
        return Proxy.newProxyInstance(Interception.class.getClassLoader(), new Class[]{cls, Intercepted.class}, interceptionHandler);
    }

    public static Object proxy(Object obj, InterceptionHandler  interceptionHandler, Class<?>... clsArr) throws IllegalArgumentException {
        interceptionHandler.setDelegatee(obj);
        return Proxy.newProxyInstance(Interception.class.getClassLoader(), clsArr, interceptionHandler);
    }

    private Interception() {
    }
}
