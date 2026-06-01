package com.zrlog.plugincore.server.web.util;

import com.hibegin.common.dao.dto.PageData;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PageDataUtilsTest {

    @Test
    public void shouldPageNewestRowsFirst() {
        PageData<String> page = PageDataUtils.newestFirst(Arrays.asList("old", "middle", "new"), 1, 2, 2);

        assertEquals(Long.valueOf(1), page.getPage());
        assertEquals(Long.valueOf(2), page.getSize());
        assertEquals(3L, page.getTotalElements());
        assertEquals(Arrays.asList("new", "middle"), page.getRows());
    }

    @Test
    public void shouldClampPageAndPageSize() {
        List<String> items = Arrays.asList("0", "1", "2", "3", "4");
        PageData<String> page = PageDataUtils.from(items, 10, 200, 2);

        assertEquals(Long.valueOf(1), page.getPage());
        assertEquals(Long.valueOf(PageDataUtils.MAX_PAGE_SIZE), page.getSize());
        assertEquals(5L, page.getTotalElements());
        assertEquals(items, page.getRows());
    }

    @Test
    public void shouldUseDefaultPageSizeWhenRequestedSizeInvalid() {
        PageData<String> page = PageDataUtils.from(Arrays.asList("0", "1", "2"), 1, 0, 2);

        assertEquals(Long.valueOf(2), page.getSize());
        assertEquals(Arrays.asList("0", "1"), page.getRows());
    }
}
