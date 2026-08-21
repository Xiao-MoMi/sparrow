package net.momirealms.sparrow.common.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class ReflectionUtils {
    private ReflectionUtils() {
    }

    public static Class<?> getClazz(String... classes) {
        for (String className : classes) {
            Class<?> clazz = getClazz(className);
            if (clazz != null) {
                return clazz;
            }
        }
        return null;
    }

    public static Class<?> getClazz(String clazz) {
        try {
            return Class.forName(clazz);
        } catch (Throwable e) {
            return null;
        }
    }

    public static boolean classExists(@NotNull final String clazz) {
        try {
            Class.forName(clazz);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Nullable
    public static Field getDeclaredField(final Class<?> clazz, final String field) {
        try {
            return setAccessible(clazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Nullable
    public static Field getDeclaredField(@NotNull Class<?> clazz, @NotNull String... possibleNames) {
        List<String> possibleNameList = Arrays.asList(possibleNames);
        for (Field field : clazz.getDeclaredFields()) {
            if (possibleNameList.contains(field.getName())) {
                return field;
            }
        }
        return null;
    }

    @Nullable
    public static Field getDeclaredField(final Object owner, final Class<?> type, int index) {
        if (owner == null) {
            return null;
        }
        int i = 0;
        Class<?> clazz = owner.getClass();
        while (clazz != null && clazz != Object.class) {
            for (final Field field : clazz.getDeclaredFields()) {
                if (field.getType() == type) {
                    if (index == i) {
                        return setAccessible(field);
                    }
                    i++;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    @Nullable
    public static Field getDeclaredField(final Class<?> clazz, final Class<?> type, int index) {
        int i = 0;
        for (final Field field : clazz.getDeclaredFields()) {
            if (field.getType() == type) {
                if (index == i) {
                    return setAccessible(field);
                }
                i++;
            }
        }
        return null;
    }

    @Nullable
    public static Method getMethod(final Class<?> clazz, Class<?> returnType, final String[] possibleMethodNames, final Class<?>... parameterTypes) {
        outer:
        for (Method method : clazz.getMethods()) {
            if (method.getParameterCount() != parameterTypes.length) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] != parameterTypes[i]) {
                    continue outer;
                }
            }
            for (String name : possibleMethodNames) {
                if (name.equals(method.getName())) {
                    if (returnType.isAssignableFrom(method.getReturnType())) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public static Method getMethod(final Class<?> clazz, Class<?> returnType, final Class<?>... parameterTypes) {
        outer:
        for (Method method : clazz.getMethods()) {
            if (method.getParameterCount() != parameterTypes.length) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] != parameterTypes[i]) {
                    continue outer;
                }
            }
            if (returnType.isAssignableFrom(method.getReturnType())) return method;
        }
        return null;
    }

    @NotNull
    public static <T extends AccessibleObject> T setAccessible(@NotNull final T o) {
        try {
            o.setAccessible(true);
            return o;
        } catch (Throwable e) {
            return o;
        }
    }

    @Nullable
    public static Constructor<?> getConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            return clazz.getConstructor(parameterTypes);
        } catch (NoSuchMethodException | SecurityException ignore) {
            return null;
        }
    }

    @Nullable
    public static Constructor<?> getConstructor(Class<?> clazz, int index) {
        try {
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            if (index < 0 || index >= constructors.length) {
                throw new IndexOutOfBoundsException("Invalid constructor index: " + index);
            }
            return setAccessible(constructors[index]);
        } catch (SecurityException e) {
            return null;
        }
    }
}
