package com.taskflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the PagedResponse&lt;T&gt; shape from src/types/index.ts.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PagedResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> content = new ArrayList<>();
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
