package cn.aifei.cache.release;

import cn.aifei.cache.regression.RegressionTestSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ReleaseArtifactIT {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    private Path artifact() {
        String path = System.getProperty("release.artifact");
        assertNotNull("Run mvn verify to build and check the release artifact", path);
        Path artifact = Paths.get(path);
        assertTrue("Missing release artifact: " + artifact, Files.isRegularFile(artifact));
        return artifact;
    }

    private URLClassLoader isolated(String... forbidden) throws Exception {
        Set<URL> urls = new LinkedHashSet<>();
        urls.add(artifact().toUri().toURL());
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        for (String entry : classpath.split(File.pathSeparator)) {
            // Exclude target/classes and target/test-classes: exercise the packaged production code.
            File file = new File(entry);
            if (!file.isFile() || !entry.endsWith(".jar")) continue;
            String normalized = entry.replace(File.separatorChar, '/');
            boolean allowed = true;
            for (String name : forbidden) if (normalized.contains(name)) allowed = false;
            if (allowed) urls.add(file.toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), null);
    }

    @Test public void publishedCaffeineAndPluginWorkWithoutRedisOrFuryDependencies() throws Exception {
        try (URLClassLoader loader = isolated("/jedis/", "/fury-core/", "/guava/", "/redis-authx-core/", "/commons-pool2/")) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                Class<?> type = loader.loadClass("cn.aifei.cache.caffeine.CaffeineCache");
                Class<?> api = loader.loadClass("cn.aifei.cache.Cache");
                Class<?> countApi = loader.loadClass("cn.aifei.cache.Counter");
                Object cache = type.newInstance();
                api.getMethod("put", String.class, String.class, Object.class, int.class).invoke(cache, "n", "k", "v", 30);
                assertEquals("v", api.getMethod("get", String.class, String.class).invoke(cache, "n", "k"));
                Class<?> pluginType = loader.loadClass("cn.aifei.cache.CachePlugin");
                Object plugin = pluginType.getConstructor(api).newInstance(cache);
                pluginType.getMethod("start").invoke(plugin); pluginType.getMethod("start").invoke(plugin);
                Class<?> aop = loader.loadClass("cn.aifei.aop.Aop");
                assertSame(cache, aop.getMethod("get", Class.class).invoke(null, api));
                Object counter = aop.getMethod("get", Class.class).invoke(null, countApi);
                assertEquals(1L, countApi.getMethod("increase", String.class, String.class, long.class, int.class)
                        .invoke(counter, "n", "k", 1L, 30));
                pluginType.getMethod("stop").invoke(plugin); pluginType.getMethod("stop").invoke(plugin);
            } finally { Thread.currentThread().setContextClassLoader(previous); }
        }
    }

    @Test public void publishedRedisCustomCodecWorksWithoutFuryOrCaffeine() throws Exception {
        try (RegressionTestSupport.Backend b = new RegressionTestSupport.Backend("redis-resp2");
             URLClassLoader loader = isolated("/caffeine/", "/fury-core/", "/guava/")) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                Class<?> codecType = loader.loadClass("cn.aifei.cache.redis.RedisValueCodec");
                Object codec = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] {codecType}, (proxy, method, args) -> {
                    if (method.getName().equals("serialize")) return args[0].toString().getBytes(StandardCharsets.UTF_8);
                    if (method.getName().equals("deserialize")) return new String((byte[]) args[0], StandardCharsets.UTF_8);
                    if (method.getName().equals("toString")) return "TestCodec";
                    throw new AssertionError(method);
                });
                Class<?> configType = loader.loadClass("cn.aifei.cache.redis.RedisConfig");
                Object config = configType.newInstance();
                configType.getMethod("uri", String.class).invoke(config, RegressionTestSupport.REDIS_URI);
                configType.getMethod("valueCodec", codecType).invoke(config, codec);
                Class<?> cacheType = loader.loadClass("cn.aifei.cache.redis.RedisCache");
                Object cache = cacheType.getConstructor(configType).newInstance(config);
                try {
                    cacheType.getMethod("put", String.class, String.class, Object.class, Duration.class)
                            .invoke(cache, b.name("optional"), "k", "v", Duration.ofSeconds(30));
                    assertEquals("v", cacheType.getMethod("get", String.class, String.class).invoke(cache, b.name("optional"), "k"));
                    Object counter = cacheType.getMethod("createCounter").invoke(cache);
                    Class<?> countApi = loader.loadClass("cn.aifei.cache.Counter");
                    assertEquals(1L, countApi.getMethod("increase", String.class, String.class, long.class, int.class)
                            .invoke(counter, b.name("optional"), "k", 1L, 30));
                } finally { ((AutoCloseable) cache).close(); }
            } finally { Thread.currentThread().setContextClassLoader(previous); }
        }
    }

    @Test public void publicBusinessInterfacesExposeOnlyJdkTypes() throws Exception {
        try (URLClassLoader loader = isolated()) {
            for (String name : new String[] {"cn.aifei.cache.Cache", "cn.aifei.cache.Counter"}) {
                for (Method method : loader.loadClass(name).getMethods()) {
                    List<Class<?>> types = new ArrayList<>(Arrays.asList(method.getParameterTypes()));
                    types.add(method.getReturnType());
                    for (Class<?> type : types) assertTrue(method.toString(), type.isPrimitive() || type.getName().startsWith("java."));
                }
            }
        }
    }

    private URLClassLoader schemaLoader(String version, String fields) throws Exception {
        Path directory = temporary.newFolder("schema-" + version).toPath();
        Path source = directory.resolve("Item.java");
        Files.write(source, ("package cn.aifei.cache.release.fixture; public class Item { public long id; " + fields + " }")
                .getBytes(StandardCharsets.UTF_8));
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull("schema compatibility test requires a JDK", compiler);
        assertEquals(0, compiler.run(null, null, null, "-source", "8", "-target", "8", "-d", directory.toString(), source.toString()));
        try (URLClassLoader base = isolated("/caffeine/")) {
            List<URL> urls = new ArrayList<>(Arrays.asList(base.getURLs()));
            urls.add(directory.toUri().toURL());
            return new URLClassLoader(urls.toArray(new URL[0]), null);
        }
    }

    private Object newRedisCache(URLClassLoader loader) throws Exception {
        Class<?> configType = loader.loadClass("cn.aifei.cache.redis.RedisConfig");
        Object config = configType.newInstance();
        configType.getMethod("uri", String.class).invoke(config, RegressionTestSupport.REDIS_URI);
        return loader.loadClass("cn.aifei.cache.redis.RedisCache").getConstructor(configType).newInstance(config);
    }

    @Test public void furyRollingSchemaSupportsAddedAndRemovedPojoFieldsBothWays() throws Exception {
        try (RegressionTestSupport.Backend b = new RegressionTestSupport.Backend("redis-resp2");
             URLClassLoader oldLoader = schemaLoader("old", "public String removed;");
             URLClassLoader newLoader = schemaLoader("new", "public String added;")) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Object oldCache = null, newCache = null;
            String name = b.name("rolling");
            try {
                Thread.currentThread().setContextClassLoader(oldLoader);
                oldCache = newRedisCache(oldLoader);
                Class<?> oldModel = oldLoader.loadClass("cn.aifei.cache.release.fixture.Item");
                Object oldItem = oldModel.newInstance();
                oldModel.getField("id").setLong(oldItem, 9007199254740993L);
                oldModel.getField("removed").set(oldItem, "old field");
                oldCache.getClass().getMethod("put", String.class, String.class, Object.class, Duration.class)
                        .invoke(oldCache, name, "k", oldItem, Duration.ofSeconds(30));
                Thread.currentThread().setContextClassLoader(newLoader);
                newCache = newRedisCache(newLoader);
                Object newItem = newCache.getClass().getMethod("get", String.class, String.class).invoke(newCache, name, "k");
                assertEquals(9007199254740993L, newItem.getClass().getField("id").getLong(newItem));
                assertNull(newItem.getClass().getField("added").get(newItem));
                newItem.getClass().getField("added").set(newItem, "new field");
                newCache.getClass().getMethod("put", String.class, String.class, Object.class, Duration.class)
                        .invoke(newCache, name, "k", newItem, Duration.ofSeconds(30));
                Thread.currentThread().setContextClassLoader(oldLoader);
                Object roundTrip = oldCache.getClass().getMethod("get", String.class, String.class).invoke(oldCache, name, "k");
                assertEquals(9007199254740993L, oldModel.getField("id").getLong(roundTrip));
                assertNull(oldModel.getField("removed").get(roundTrip));
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
                try { if (oldCache != null) ((AutoCloseable) oldCache).close(); }
                finally { if (newCache != null) ((AutoCloseable) newCache).close(); }
            }
        }
    }

    @Test public void artifactContainsJava8ClassesAndActualSourceAndJavadoc() throws Exception {
        Path main = artifact();
        try (ZipFile jar = new ZipFile(main.toFile())) {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                assertFalse("test class was packaged: " + entry.getName(),
                        entry.getName().contains("Test") || entry.getName().startsWith("cn/aifei/cache/release/"));
                if (entry.getName().endsWith(".class")) {
                    try (DataInputStream in = new DataInputStream(jar.getInputStream(entry))) {
                        assertEquals(0xcafebabe, in.readInt()); in.readUnsignedShort();
                        assertTrue("requires newer Java: " + entry.getName(), in.readUnsignedShort() <= 52);
                        count++;
                    }
                }
            }
            assertTrue(count >= 16);
        }
        try (ZipFile source = new ZipFile(main.toString().replace(".jar", "-sources.jar"));
             ZipFile docs = new ZipFile(main.toString().replace(".jar", "-javadoc.jar"))) {
            assertNotNull(source.getEntry("cn/aifei/cache/Cache.java"));
            assertNotNull(source.getEntry("cn/aifei/cache/Counter.java"));
            assertNotNull(docs.getEntry("cn/aifei/cache/Cache.html"));
            assertNotNull(docs.getEntry("cn/aifei/cache/Counter.html"));
        }
    }
}
