package com.taskflow.bean;

import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.util.DateUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * View-side helper accessible from EL as <code>#{view}</code>.
 *
 * <p>Wraps {@link DateUtil} and the {@link TaskStatus} enum so XHTML can call
 * formatting helpers without a custom EL function library — this keeps the
 * markup parallel to the React JSX which calls helpers directly.
 */
@Named("viewHelper")
@ApplicationScoped
public class ViewHelper {

    private static final List<TaskStatus> ALL_STATUSES =
            Arrays.asList(TaskStatus.values());

    public List<TaskStatus> getStatuses() {
        return ALL_STATUSES;
    }

    public String formatDate(final OffsetDateTime when) {
        return DateUtil.formatDate(when);
    }

    public String formatDateTime(final OffsetDateTime when) {
        return DateUtil.formatDateTime(when);
    }

    public String formatRelative(final OffsetDateTime when) {
        return DateUtil.formatRelative(when);
    }

    public boolean isOverdue(final Task t) {
        return t != null && DateUtil.isOverdue(t.getDueDate());
    }

    public boolean isDueSoon(final Task t) {
        return t != null && DateUtil.isDueSoon(t.getDueDate());
    }

    public String dueClass(final Task t) {
        if (t == null || t.getDueDate() == null) return "task-card__due task-card__due--none";
        StringBuilder sb = new StringBuilder("task-card__due");
        if (DateUtil.isOverdue(t.getDueDate()))      sb.append(" task-card__due--overdue");
        else if (DateUtil.isDueSoon(t.getDueDate())) sb.append(" task-card__due--soon");
        return sb.toString();
    }
}
