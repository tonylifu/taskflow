package com.taskflow.dto;

import com.taskflow.model.TaskStatus;

import java.io.Serializable;

/**
 * Mirrors the TaskQueryParams interface from src/types/index.ts.
 *
 * <p>Used as backing state for the filter row, the pager, and as the
 * query string for outbound calls to the Tasks API.
 */
public class TaskQueryParams implements Serializable {

    private static final long serialVersionUID = 1L;

    private TaskStatus status;
    private String title;
    private Integer page = 0;
    private Integer size = 12;
    private String sortBy = "createdAt";
    private String direction = "DESC";

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public boolean hasFilters() {
        return status != null || (title != null && !title.isBlank());
    }
}
