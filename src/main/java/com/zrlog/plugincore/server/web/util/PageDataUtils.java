package com.zrlog.plugincore.server.web.util;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequestImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PageDataUtils {

    public static final int MAX_PAGE_SIZE = 100;

    private PageDataUtils() {
    }

    public static <T> PageData<T> newestFirst(List<T> source, int requestedPage, int requestedPageSize, int defaultPageSize) {
        List<T> items = copyItems(source);
        Collections.reverse(items);
        return from(items, requestedPage, requestedPageSize, defaultPageSize);
    }

    public static <T> PageData<T> from(List<T> source, int requestedPage, int requestedPageSize, int defaultPageSize) {
        List<T> items = copyItems(source);
        int pageSize = normalizePageSize(requestedPageSize, defaultPageSize);
        int total = items.size();
        int maxPage = total == 0 ? 1 : (int) Math.ceil(total / (double) pageSize);
        int page = Math.min(Math.max(requestedPage, 1), maxPage);
        PageRequestImpl pageRequest = new PageRequestImpl((long) page, (long) pageSize);
        int fromIndex = total == 0 ? 0 : pageRequest.getOffset().intValue();
        int toIndex = Math.min(total, fromIndex + pageSize);
        List<T> rows = fromIndex >= toIndex ? Collections.emptyList() : new ArrayList<T>(items.subList(fromIndex, toIndex));
        PageData<T> pageData = new PageData<T>((long) total, rows, pageRequest.getPage(), pageRequest.getSize());
        pageData.setDefaultPageSize((long) defaultPageSize);
        return pageData;
    }

    private static <T> List<T> copyItems(List<T> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<T>();
        }
        return new ArrayList<T>(source);
    }

    private static int normalizePageSize(int requestedPageSize, int defaultPageSize) {
        int fallback = defaultPageSize <= 0 ? MAX_PAGE_SIZE : defaultPageSize;
        if (requestedPageSize <= 0) {
            return Math.min(fallback, MAX_PAGE_SIZE);
        }
        return Math.min(requestedPageSize, MAX_PAGE_SIZE);
    }
}
