package com.zrlog.plugincore.server.runtime.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RuntimePageTest {

    @Test
    public void shouldPageNewestItemsFirst() {
        RuntimePage<String> page = RuntimePage.newestFirst(Arrays.asList("old", "middle", "new"), 1, 2, 2);

        assertEquals(1, page.getPage());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotal());
        assertEquals(Arrays.asList("new", "middle"), page.getItems());
    }

    @Test
    public void shouldClampPageAndPageSize() {
        List<String> items = Arrays.asList("0", "1", "2", "3", "4");
        RuntimePage<String> page = RuntimePage.from(items, 10, 200, 2);

        assertEquals(1, page.getPage());
        assertEquals(RuntimePage.MAX_PAGE_SIZE, page.getPageSize());
        assertEquals(5, page.getTotal());
        assertEquals(items, page.getItems());
    }

    @Test
    public void shouldUseDefaultPageSizeWhenRequestedSizeInvalid() {
        RuntimePage<String> page = RuntimePage.from(Arrays.asList("0", "1", "2"), 1, 0, 2);

        assertEquals(2, page.getPageSize());
        assertEquals(Arrays.asList("0", "1"), page.getItems());
    }
}
