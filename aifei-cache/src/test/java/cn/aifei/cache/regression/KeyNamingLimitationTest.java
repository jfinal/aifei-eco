package cn.aifei.cache.regression;

import cn.aifei.cache.Cache;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/**
 * 记录调用方命名冲突下的现有实现差异，见 docs/design.md 的命名责任。
 * 这些冲突组合不在共同隔离契约范围内；测试通过表示限制仍可复现。
 */
@RunWith(Parameterized.class)
public class KeyNamingLimitationTest {
    @Parameterized.Parameters(name="{0}") public static Object[] implementations() {
        return new Object[] {"caffeine", "redis-resp2", "redis-resp3"};
    }
    @Parameterized.Parameter public String kind;
    private RegressionTestSupport.Backend b;
    private Cache cache;
    private String n;

    @Before public void open() {
        b = new RegressionTestSupport.Backend(kind);
        cache = b.cache;
        n = b.name("naming");
    }

    @After public void close() throws Exception { if (b != null) b.close(); }

    @Test public void childClearShowsBackendDifferenceForOverlappingKeyPrefix() {
        cache.put(n, "child:parent-key", "parent", 30);
        cache.put(n, "other:parent-key", "unrelated", 30);
        cache.put(n + ":child", "different-key", "child", 30);
        assertEquals("parent", cache.get(n, "child:parent-key"));
        assertEquals("child", cache.get(n + ":child", "different-key"));

        cache.clear(n + ":child");

        assertEquals("Redis clears the overlapping physical prefix; Caffeine keeps the parent entry",
                kind.equals("caffeine") ? "parent" : null, cache.get(n, "child:parent-key"));
        assertEquals("unrelated", cache.get(n, "other:parent-key"));
        assertNull(cache.get(n + ":child", "different-key"));
    }

    @Test public void missingChildClearShowsBackendDifferenceForOverlappingKeyPrefix() {
        cache.put(n, "child:parent-key", "parent", 30);
        assertEquals("parent", cache.get(n, "child:parent-key"));

        // 未向子命名空间写入任何条目，Redis 仍会匹配父级业务 key 的前缀。
        cache.clear(n + ":child");

        assertEquals("A missing child namespace does not protect the overlapping Redis key",
                kind.equals("caffeine") ? "parent" : null, cache.get(n, "child:parent-key"));
    }

    @Test public void concatenatedKeyCollisionShowsBackendDifference() {
        cache.put(n, "child:same-key", "parent", 30);
        cache.put(n + ":child", "same-key", "child", 30);

        assertEquals("Redis treats the two naming combinations as one physical key",
                kind.equals("caffeine") ? "parent" : "child", cache.get(n, "child:same-key"));
        assertEquals("child", cache.get(n + ":child", "same-key"));

        cache.remove(n + ":child", "same-key");

        assertEquals("Removing the shared Redis key also removes the parent entry",
                kind.equals("caffeine") ? "parent" : null, cache.get(n, "child:same-key"));
        assertNull(cache.get(n + ":child", "same-key"));
    }
}
