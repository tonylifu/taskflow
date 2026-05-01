package com.taskflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskflow.dto.ApiResponse;
import com.taskflow.dto.PagedResponse;
import com.taskflow.dto.TaskPayload;
import com.taskflow.dto.TaskQueryParams;
import com.taskflow.model.Task;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Talks to the same backend Tasks REST API the React app used.
 * Mirrors src/services/taskService.ts and src/services/apiClient.ts.
 *
 * <p>Configurable via the system property / env var <code>taskflow.api.baseUrl</code>
 * (analogue of <code>VITE_API_BASE_URL</code>). Defaults to
 * <code>http://localhost:8080/api/v1</code>.
 */
@ApplicationScoped
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:8787/api/v1";

    private Client httpClient;
    private WebTarget tasksTarget;
    private ObjectMapper mapper;

    @PostConstruct
    void init() {
        String baseUrl = resolveBaseUrl();
        log.info("TaskService initialising — baseUrl={}", baseUrl);

        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.httpClient = ClientBuilder.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        this.tasksTarget = httpClient.target(baseUrl).path("tasks");
    }

    @PreDestroy
    void shutdown() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    private String resolveBaseUrl() {
        String prop = System.getProperty("taskflow.api.baseUrl");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("TASKFLOW_API_BASE_URL");
        if (env != null && !env.isBlank()) return env;
        return DEFAULT_BASE_URL;
    }

    // ─── CRUD ────────────────────────────────────────────────────────────────

    public PagedResponse<Task> getAll(TaskQueryParams params) {
        WebTarget t = tasksTarget;
        if (params.getStatus() != null) t = t.queryParam("status", params.getStatus().name());
        if (params.getTitle() != null && !params.getTitle().isBlank())
            t = t.queryParam("title", params.getTitle());
        if (params.getPage() != null)      t = t.queryParam("page",      params.getPage());
        if (params.getSize() != null)      t = t.queryParam("size",      params.getSize());
        if (params.getSortBy() != null)    t = t.queryParam("sortBy",    params.getSortBy());
        if (params.getDirection() != null) t = t.queryParam("direction", params.getDirection());

        try (Response resp = t.request(MediaType.APPLICATION_JSON).get()) {
            return readPagedResponse(resp);
        } catch (Exception e) {
            throw normalise(e);
        }
    }

    public Task getById(String id) {
        try (Response resp = tasksTarget.path(id).request(MediaType.APPLICATION_JSON).get()) {
            return readTaskResponse(resp);
        } catch (Exception e) {
            throw normalise(e);
        }
    }

    public Task create(TaskPayload payload) {
        try (Response resp = tasksTarget
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(payload))) {
            return readTaskResponse(resp);
        } catch (Exception e) {
            throw normalise(e);
        }
    }

    public Task update(String id, TaskPayload payload) {
        try (Response resp = tasksTarget.path(id)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(payload))) {
            return readTaskResponse(resp);
        } catch (Exception e) {
            throw normalise(e);
        }
    }

    public Task updateStatus(String id, TaskPayload payload) {
        // PATCH must be requested via build("PATCH") because the JAX-RS Client
        // API doesn't expose a top-level patch() helper.
        try (Response resp = (Response) tasksTarget.path(id).path("status")
                .request(MediaType.APPLICATION_JSON)
                .build("PATCH", Entity.json(payload))
                .invoke()) {
            return readTaskResponse(resp);
        } catch (Exception e) {
            throw normalise(e);
        }
    }

    public void delete(String id) {
        try (Response resp = tasksTarget.path(id).request().delete()) {
            if (resp.getStatus() >= 400) throw fromResponse(resp);
        } catch (Exception e) {
            throw normalise(e);
        }
    }

    // ─── Response handling ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Task readTaskResponse(Response resp) {
        System.out.println("read task response status: " + resp.getStatus());
        System.out.println("read task response body: " + resp);
        if (resp.getStatus() >= 400) throw fromResponse(resp);
        String body = resp.readEntity(String.class);
        try {
            ApiResponse<Task> wrapped = mapper.readValue(
                    body,
                    mapper.getTypeFactory().constructParametricType(ApiResponse.class, Task.class));
            return wrapped.getData();
        } catch (JsonProcessingException e) {
            throw new TaskApiException("Malformed response from Tasks API", e);
        }
    }

    private PagedResponse<Task> readPagedResponse(Response resp) {
        if (resp.getStatus() >= 400) throw fromResponse(resp);
        String body = resp.readEntity(String.class);
        try {
            var pageType = mapper.getTypeFactory().constructParametricType(PagedResponse.class, Task.class);
            var envType  = mapper.getTypeFactory().constructParametricType(ApiResponse.class, pageType);
            ApiResponse<PagedResponse<Task>> wrapped = mapper.readValue(body, envType);
            return wrapped.getData();
        } catch (JsonProcessingException e) {
            throw new TaskApiException("Malformed paged response from Tasks API", e);
        }
    }

    private TaskApiException fromResponse(Response resp) {
        String body = "";
        try { body = resp.readEntity(String.class); } catch (Exception ignored) {}
        String message;
        try {
            ApiResponse<?> wrapped = mapper.readValue(
                    body,
                    mapper.getTypeFactory().constructParametricType(ApiResponse.class, Object.class));
            message = wrapped.getMessage();
        } catch (Exception ignored) {
            message = null;
        }
        if (message == null || message.isBlank()) {
            message = "Request failed (HTTP " + resp.getStatus() + ")";
        }
        return new TaskApiException(message);
    }

    /**
     * Mirrors the React response interceptor's error normalisation:
     * surface a friendly message for connection problems, otherwise pass
     * through whatever the server told us.
     */
    private TaskApiException normalise(Exception e) {
        if (e instanceof TaskApiException tae) return tae;
        if (e.getCause() instanceof java.net.ConnectException
                || e instanceof jakarta.ws.rs.ProcessingException) {
            return new TaskApiException(
                    "Unable to reach the server. Please check your connection.", e);
        }
        return new TaskApiException(
                e.getMessage() == null ? "An unexpected error occurred." : e.getMessage(), e);
    }
}
