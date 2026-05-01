package com.taskflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.taskflow.model.TaskStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Mirrors CreateTaskPayload / UpdateTaskPayload from src/types/index.ts.
 *
 * <p>Null fields are omitted from the JSON body so PUT/PATCH semantics
 * (partial update) work the same way as the React app's behaviour
 * of building a payload with `?? undefined` filtering.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private String description;
    private TaskStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime dueDate;
}
