package com.taskflow.dto;

import com.taskflow.model.TaskStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * Mirrors the TaskQueryParams interface from src/types/index.ts.
 *
 * <p>Used as backing state for the filter row, the pager, and as the
 * query string for outbound calls to the Tasks API.
 */
@Data
public class TaskQueryParams implements Serializable {

    private static final long serialVersionUID = 1L;

    private TaskStatus status;
    private String title;
    private Integer page = 0;
    private Integer size = 12;
    private String sortBy = "createdAt";
    private String direction = "DESC";

    public boolean hasFilters() {
        return status != null || (title != null && !title.isBlank());
    }
}
