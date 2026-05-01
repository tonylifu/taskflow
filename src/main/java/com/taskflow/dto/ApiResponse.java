package com.taskflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * Mirrors the ApiResponse&lt;T&gt; envelope used by the backend
 * (success, message, data, timestamp).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private T data;
    private String timestamp;
}
