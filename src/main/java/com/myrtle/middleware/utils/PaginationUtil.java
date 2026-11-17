package com.myrtle.middleware.utils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class PaginationUtil {

    public static <T> Page<T> paginateList(List<T> list, int page, int size) {
        int start = Math.min(page * size, list.size());
        int end = Math.min(start + size, list.size());
        List<T> paginatedList = list.subList(start, end);
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(paginatedList, pageable, list.size());
    }
}
