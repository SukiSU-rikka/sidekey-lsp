package com.sidekey.lsp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 调隐藏 API 用的极简反射（system_server 不受隐藏 API 限制） */
final class Refl {
    private Refl() {}

    static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass();
        Object recv = target instanceof Class ? null : target;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                if (!fits(m.getParameterTypes(), args)) continue;
                m.setAccessible(true);
                return m.invoke(recv, args);
            }
        }
        throw new NoSuchMethodException(c.getName() + "#" + name);
    }

    static Object getStatic(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static boolean fits(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Object a = args[i];
            Class<?> t = box(types[i]);
            if (a == null) { if (types[i].isPrimitive()) return false; continue; }
            if (!t.isInstance(a)) return false;
        }
        return true;
    }

    private static Class<?> box(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == boolean.class) return Boolean.class;
        if (t == float.class) return Float.class;
        if (t == double.class) return Double.class;
        if (t == short.class) return Short.class;
        if (t == byte.class) return Byte.class;
        return Character.class;
    }
}
