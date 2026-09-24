package net.momirealms.sparrow.plugin.classpath;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

/**
 * 抽象的 URLClassLoader 访问器类.
 * 用于在运行时绕过 Java 的模块系统和访问控制限制, 动态地将 URL 注入到现有的 URLClassLoader 中.
 */
public abstract class URLClassLoaderAccess {

    /**
     * 创建一个适合当前环境的 URLClassLoaderAccess 实例. <br>
     * 1. 若反射机制可用, 则返回基于反射的实现. <br>
     * 2. 若Unsafe机制可用, 则返回基于Unsafe的实现. <br>
     * 3. 若皆不可用, 返回一个空操作的 (Noop) 实例. <br>
     *
     * @param classLoader 需要注入 URL 的目标 URLClassLoader.
     * @return 适合当前环境的 URLClassLoaderAccess 实例.
     */
    public static URLClassLoaderAccess create(URLClassLoader classLoader) {
        if (Reflection.isSupported()) {
            return new Reflection(classLoader);
        } else if (Unsafe.isSupported()) {
            return new Unsafe(classLoader);
        } else {
            return Noop.INSTANCE;
        }
    }

    private final URLClassLoader classLoader;

    protected URLClassLoaderAccess(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * 向目标类加载器中动态添加 URL.
     *
     * @param url 需要添加的 URL 对象.
     */
    public abstract void addURL(@NotNull URL url);

    /**
     * 抛出包含特定指导信息的 UnsupportedOperationException.
     * 提示用户可以通过添加特定的 JVM 启动参数来解决注入失败的问题.
     *
     * @param cause 导致失败的根本异常原因.
     * @throws UnsupportedOperationException 始终抛出该异常.
     */
    private static void throwError(Throwable cause) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("""
                Plugin is unable to inject into the plugin URLClassLoader.
                You may be able to fix this problem by adding the following command-line argument \
                directly after the 'java' command in your start script:\s
                '--add-opens java.base/java.lang=ALL-UNNAMED'""", cause);
    }

    /**
     * 基于反射机制的 URLClassLoader 访问器实现.
     */
    private static class Reflection extends URLClassLoaderAccess {
        private static final Method ADD_URL_METHOD;

        static {
            Method addUrlMethod;
            try {
                addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addUrlMethod.setAccessible(true);
            } catch (Exception e) {
                addUrlMethod = null;
            }
            ADD_URL_METHOD = addUrlMethod;
        }

        /**
         * 检查当前环境是否支持反射方式注入.
         *
         * @return 如果 addURL 方法成功获取则返回 true, 否则返回 false.
         */
        private static boolean isSupported() {
            return ADD_URL_METHOD != null;
        }

        Reflection(URLClassLoader classLoader) {
            super(classLoader);
        }

        /**
         * 使用反射调用 URLClassLoader 的 addURL 方法注入 URL.
         *
         * @param url 需要添加的 URL.
         */
        @Override
        public void addURL(@NotNull URL url) {
            try {
                ADD_URL_METHOD.invoke(super.classLoader, url);
            } catch (ReflectiveOperationException e) {
                URLClassLoaderAccess.throwError(e);
            }
        }
    }

    /**
     * 基于 Unsafe 的 URLClassLoader 访问器实现.
     */
    @SuppressWarnings("removal")
    private static class Unsafe extends URLClassLoaderAccess {
        private static final sun.misc.Unsafe UNSAFE;

        static {
            sun.misc.Unsafe unsafe;
            try {
                Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            } catch (Throwable t) {
                unsafe = null;
            }
            UNSAFE = unsafe;
        }

        /**
         * 检查当前环境是否支持 Unsafe 方式注入.
         *
         * @return 如果 UNSAFE 实例成功获取则返回 true, 否则返回 false.
         */
        private static boolean isSupported() {
            return UNSAFE != null;
        }

        private final Collection<URL> unopenedURLs;
        private final Collection<URL> pathURLs;

        /**
         * 构造函数, 通过 Unsafe 获取 URLClassPath 内部的 url 集合.
         *
         * @param classLoader 目标类加载器.
         */
        @SuppressWarnings("unchecked")
        Unsafe(URLClassLoader classLoader) {
            super(classLoader);

            Collection<URL> unopenedURLs;
            Collection<URL> pathURLs;
            try {
                Object ucp = fetchField(URLClassLoader.class, classLoader, "ucp");
                unopenedURLs = (Collection<URL>) fetchField(ucp.getClass(), ucp, "unopenedUrls");
                pathURLs = (Collection<URL>) fetchField(ucp.getClass(), ucp, "path");
            } catch (Throwable e) {
                unopenedURLs = null;
                pathURLs = null;
            }

            this.unopenedURLs = unopenedURLs;
            this.pathURLs = pathURLs;
        }

        /**
         * 使用 Unsafe 强制获取对象内部字段的值.
         *
         * @param clazz  字段所在的类.
         * @param object 目标对象实例.
         * @param name   字段名称.
         * @return 字段的值.
         * @throws NoSuchFieldException 如果指定的字段不存在.
         */
        private static Object fetchField(final Class<?> clazz, final Object object, final String name) throws NoSuchFieldException {
            Field field = clazz.getDeclaredField(name);
            @SuppressWarnings("deprecation") // java18
            long offset = UNSAFE.objectFieldOffset(field);
            return UNSAFE.getObject(object, offset);
        }

        /**
         * 直接将 URL 添加到 URLClassPath 的内部集合中.
         *
         * @param url 需要添加的 URL.
         */
        @Override
        public void addURL(@NotNull URL url) {
            if (this.unopenedURLs == null || this.pathURLs == null) {
                URLClassLoaderAccess.throwError(new NullPointerException("unopenedURLs or pathURLs"));
            }

            synchronized (this.unopenedURLs)  {
                this.unopenedURLs.add(url);
                this.pathURLs.add(url);
            }
        }
    }

    /**
     * 空操作的访问器实现.
     */
    private static class Noop extends URLClassLoaderAccess {
        private static final Noop INSTANCE = new Noop();

        private Noop() {
            super(null);
        }

        /**
         * 尝试添加 URL 时始终抛出异常.
         *
         * @param url 需要添加的 URL.
         */
        @Override
        public void addURL(@NotNull URL url) {
            URLClassLoaderAccess.throwError(null);
        }
    }
}
