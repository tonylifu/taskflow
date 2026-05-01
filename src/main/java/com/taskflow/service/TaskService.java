package com.taskflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST client for the Tasks API.
 *
 * <p>Mirrors the responsibilities of {@code src/services/taskService.ts}
 * and {@code src/services/apiClient.ts} from the original React app: a thin,
 * stateless wrapper over CRUD endpoints that always returns the unwrapped
 * domain object and surfaces a {@link TaskApiException} with a user-friendly
 * message on any failure.
 *
 * <p>Configuration order of precedence:
 * <ol>
 *   <li>System property {@code taskflow.api.baseUrl}</li>
 *   <li>Environment variable {@code TASKFLOW_API_BASE_URL}</li>
 *   <li>Default: {@code http://localhost:8787/api/v1}</li>
 * </ol>
 */
@ApplicationScoped
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String DEFAULT_BASE_URL   = "http://hmcts-task-manager:8080/api/v1";
    private static final long   CONNECT_TIMEOUT_S  = 5;
    private static final long   READ_TIMEOUT_S     = 15;

    // Reused TypeReferences — created once, used per response. Cheap to reuse,
    // expensive to construct repeatedly (each one walks the generic tree).
    private static final TypeReference<ApiResponse<Task>> TASK_REF =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<PagedResponse<Task>>> PAGE_REF =
            new TypeReference<>() {};
    private static final TypeReference<ApiResponse<Object>> OBJECT_REF =
            new TypeReference<>() {};

    private Client     httpClient;
    private WebTarget  tasksTarget;
    private ObjectMapper mapper;

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        String baseUrl = resolveBaseUrl();
        log.info("TaskService initialising — baseUrl={}", baseUrl);

        this.mapper = buildMapper();

        this.httpClient = ClientBuilder.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .build();

        this.tasksTarget = httpClient.target(baseUrl).path("tasks");
    }

    @PreDestroy
    void shutdown() {
        if (httpClient != null) httpClient.close();
    }

    private static ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static String resolveBaseUrl() {
        String prop = System.getProperty("taskflow.api.baseUrl");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("TASKFLOW_API_BASE_URL");
        if (env != null && !env.isBlank()) return env;
        return DEFAULT_BASE_URL;
    }

    /**
     * Hook for subclasses (or future SSO integration) to attach a Bearer token.
     * Default: no auth header. Mirrors the React {@code apiClient.ts}
     * request interceptor that read {@code localStorage.getItem('auth_token')}.
     */
    protected String getAuthToken() {
        return null;
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    public PagedResponse<Task> getAll(TaskQueryParams params) {
        WebTarget t = applyQueryParams(tasksTarget, params);
        return execute("GET", t, null, PAGE_REF);
    }

    public Task getById(String id) {
        return execute("GET", tasksTarget.path(id), null, TASK_REF);
    }

    public Task create(TaskPayload payload) {
        return execute("POST", tasksTarget, payload, TASK_REF);
    }

    public Task update(String id, TaskPayload payload) {
        return execute("PUT", tasksTarget.path(id), payload, TASK_REF);
    }

    public Task updateStatus(String id, TaskPayload payload) {
        return execute("PATCH", tasksTarget.path(id).path("status"), payload, TASK_REF);
    }

    public void delete(String id) {
        execute("DELETE", tasksTarget.path(id), null, OBJECT_REF);
    }

    // ─── Single point of HTTP invocation ─────────────────────────────────────

    /**
     * Execute one HTTP call against the Tasks API and return the unwrapped
     * {@code data} payload from the {@link ApiResponse} envelope.
     *
     * @param method      HTTP verb (GET / POST / PUT / PATCH / DELETE)
     * @param target      the WebTarget identifying the endpoint
     * @param body        the request body — null for GET/DELETE, a payload otherwise
     * @param responseRef the typed envelope to deserialize the response into
     * @return the unwrapped {@code data} field of the envelope (may be null for DELETE)
     * @throws TaskApiException on any transport, protocol, or deserialization failure
     */
    private <T> T execute(String method,
                          WebTarget target,
                          Object body,
                          TypeReference<ApiResponse<T>> responseRef) {
        long start = System.nanoTime();
        Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

        String token = getAuthToken();
        if (token != null && !token.isBlank()) {
            builder = builder.header("Authorization", "Bearer " + token);
        }

        Entity<?> entity = (body == null) ? null : Entity.json(body);

        try (Response resp = builder.build(method, entity).invoke()) {
            int status = resp.getStatus();
            String responseBody = resp.hasEntity() ? resp.readEntity(String.class) : "";

            if (log.isDebugEnabled()) {
                log.debug("{} {} → {} ({} ms)",
                        method, target.getUri(), status, elapsedMs(start));
            }

            if (status >= 400) {
                throw buildErrorException(method, target, status, responseBody);
            }

            if (responseBody.isEmpty()) {
                return null; // DELETE / 204 No Content
            }

            return mapper.readValue(responseBody, responseRef).getData();

        } catch (TaskApiException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Malformed response from Tasks API on {} {}: {}",
                    method, target.getUri(), e.getMessage());
            throw new TaskApiException("Received an invalid response from the server.", e);
        } catch (ProcessingException e) {
            throw networkException(method, target, e);
        } catch (Exception e) {
            log.warn("Unexpected error calling {} {}: {}",
                    method, target.getUri(), e.toString());
            throw new TaskApiException(
                    e.getMessage() == null ? "An unexpected error occurred." : e.getMessage(), e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static WebTarget applyQueryParams(WebTarget base, TaskQueryParams p) {
        WebTarget t = base;
        Map<String, Object> qp = Map.of();
        // Explicit conditional appends — Map.of() is just here for readability;
        // real appends below avoid null/blank values to keep URLs clean.
        if (p.getStatus()    != null)                                    t = t.queryParam("status",    p.getStatus().name());
        if (p.getTitle()     != null && !p.getTitle().isBlank())         t = t.queryParam("title",     p.getTitle());
        if (p.getPage()      != null)                                    t = t.queryParam("page",      p.getPage());
        if (p.getSize()      != null)                                    t = t.queryParam("size",      p.getSize());
        if (p.getSortBy()    != null)                                    t = t.queryParam("sortBy",    p.getSortBy());
        if (p.getDirection() != null)                                    t = t.queryParam("direction", p.getDirection());
        return t;
    }

    /**
     * Try to extract the {@code message} field from the error envelope; fall back
     * to a generic "HTTP {status}" message if the body is missing or unparseable.
     */
    private TaskApiException buildErrorException(String method, WebTarget target,
                                                 int status, String body) {
        String message = null;
        if (body != null && !body.isBlank()) {
            try {
                ApiResponse<Object> wrapped = mapper.readValue(body, OBJECT_REF);
                message = wrapped.getMessage();
            } catch (IOException parseError) {
                log.debug("Could not parse error body for {} {}: {}",
                        method, target.getUri(), parseError.getMessage());
            }
        }
        if (message == null || message.isBlank()) {
            message = "Request failed (HTTP " + status + ")";
        }
        log.warn("{} {} → HTTP {}: {}", method, target.getUri(), status, message);
        return new TaskApiException(message);
    }

    /**
     * Map low-level networking failures to the friendly message the React app
     * showed via its axios response interceptor.
     */
    private TaskApiException networkException(String method, WebTarget target,
                                              ProcessingException e) {
        Throwable cause = e.getCause();
        String reason;
        if (cause instanceof ConnectException)        reason = "connection refused";
        else if (cause instanceof UnknownHostException) reason = "unknown host";
        else if (cause instanceof SocketTimeoutException) reason = "timeout";
        else                                          reason = e.getMessage();

        log.warn("Network failure on {} {}: {}", method, target.getUri(), reason);
        return new TaskApiException(
                "Unable to reach the server. Please check your connection.", e);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}