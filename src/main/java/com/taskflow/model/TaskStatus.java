package com.taskflow.model;

/**
 * Mirrors the TaskStatus union type from the React app's types/index.ts.
 * Display labels and CSS class hooks are kept here so the view layer never
 * has to hand-stringify enum names.
 */
public enum TaskStatus {

    TODO("To Do", "badge--todo"),
    IN_PROGRESS("In Progress", "badge--in-progress"),
    ON_HOLD("On Hold", "badge--on-hold"),
    DONE("Done", "badge--done"),
    CANCELLED("Cancelled", "badge--cancelled");

    private final String label;
    private final String badgeClass;

    TaskStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
