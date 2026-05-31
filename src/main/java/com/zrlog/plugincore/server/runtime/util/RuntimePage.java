package com.zrlog.plugincore.server.runtime.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RuntimePage<T> {

    public static final int MAX_PAGE_SIZE = 100;

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int total;

    private RuntimePage(List<T> items, int page, int pageSize, int total) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public static <T> RuntimePage<T> newestFirst(List<T> source, int requestedPage, int requestedPageSize, int defaultPageSize) {
        List<T> items = copyItems(source);
        Collections.reverse(items);
        return from(items, requestedPage, requestedPageSize, defaultPageSize);
    }

    public static <T> RuntimePage<T> from(List<T> source, int requestedPage, int requestedPageSize, int defaultPageSize) {
        List<T> items = copyItems(source);
        int pageSize = normalizePageSize(requestedPageSize, defaultPageSize);
        int total = items.size();
        int maxPage = total == 0 ? 1 : (int) Math.ceil(total / (double) pageSize);
        int page = Math.min(Math.max(1, requestedPage), maxPage);
        int fromIndex = total == 0 ? 0 : (page - 1) * pageSize;
        int toIndex = Math.min(total, fromIndex + pageSize);
        List<T> pageItems = fromIndex >= toIndex ? Collections.emptyList() : new ArrayList<T>(items.subList(fromIndex, toIndex));
        return new RuntimePage<T>(pageItems, page, pageSize, total);
    }

    private static int normalizePageSize(int requestedPageSize, int defaultPageSize) {
        int fallback = defaultPageSize > 0 ? defaultPageSize : 20;
        if (requestedPageSize <= 0) {
            return Math.min(fallback, MAX_PAGE_SIZE);
        }
        return Math.min(requestedPageSize, MAX_PAGE_SIZE);
    }

    private static <T> List<T> copyItems(List<T> source) {
        return source == null ? new ArrayList<T>() : new ArrayList<T>(source);
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotal() {
        return total;
    }
}
