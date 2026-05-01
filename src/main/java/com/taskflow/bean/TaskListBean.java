package com.taskflow.bean;

import com.taskflow.dto.PagedResponse;
import com.taskflow.dto.TaskPayload;
import com.taskflow.dto.TaskQueryParams;
import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.service.TaskApiException;
import com.taskflow.service.TaskService;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.primefaces.PrimeFaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Backing bean for the TaskFlow page.
 *
 * <p>This single ViewScoped bean replaces the combination of:
 * <ul>
 *   <li><code>TaskList</code> React component (page state and orchestration),
 *   <li>the <code>useTasks</code> / <code>useCreateTask</code> / <code>useUpdateTask</code>
 *       / <code>useDeleteTask</code> / <code>useUpdateTaskStatus</code> TanStack Query hooks
 *       (data fetching, optimistic refresh, toast feedback).
 * </ul>
 *
 * <p>State that lived in <code>useState</code> in the React app — query params,
 * the current page of tasks, modal open/close, the task being edited or deleted,
 * the loading/error state — is held here as bean fields. AJAX listeners replace
 * the React handlers; PrimeFaces growl messages stand in for the toast notifications.
 */
@Named
@ViewScoped
public class TaskListBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(TaskListBean.class);

    @Inject private transient TaskService taskService;

    // ─── Filter / paging state (mirrors useState<TaskQueryParams>) ───────────

    private final TaskQueryParams params = new TaskQueryParams();

    // ─── Data state (mirrors React Query result) ─────────────────────────────

    private PagedResponse<Task> page;
    private boolean loading;
    private String loadError;

    // ─── Modal state (mirrors useState for editing/deleting) ─────────────────

    private boolean modalOpen;
    private boolean modalEditMode;
    private Task editingTask;            // null when creating
    private Task deletingTask;           // non-null while delete confirm is open
    private boolean submitting;

    // Form fields for create/edit (drives the dialog inputs)
    private String formTitle;
    private String formDescription;
    private TaskStatus formStatus = TaskStatus.TODO;
    private OffsetDateTime formDueDate;

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        loadTasks();
    }

    // ─── Data loading ────────────────────────────────────────────────────────

    /**
     * Hits the Tasks API with current params. Mirrors the useTasks query.
     * Called on init, when filters/sort/page change, and after every mutation
     * so the list stays in sync.
     */
    public void loadTasks() {
        loading = true;
        loadError = null;
        try {
            page = taskService.getAll(params);
        } catch (TaskApiException e) {
            log.warn("Failed to load tasks: {}", e.getMessage());
            loadError = e.getMessage();
            page = null;
        } finally {
            loading = false;
        }
    }

    // ─── Filter handlers ─────────────────────────────────────────────────────

    /** Called when the search input changes (debounced via p:remoteCommand on the client). */
    public void onSearchChanged() {
        params.setPage(0);
        loadTasks();
    }

    public void onStatusChanged()    { params.setPage(0); loadTasks(); }
    public void onDirectionChanged() { params.setPage(0); loadTasks(); }

    public void clearFilters() {
        params.setStatus(null);
        params.setTitle(null);
        params.setPage(0);
        loadTasks();
    }

    // ─── Pagination handlers ─────────────────────────────────────────────────

    public void goToPage(int target) {
        if (page == null) return;
        if (target < 0 || target >= page.getTotalPages()) return;
        params.setPage(target);
        loadTasks();
    }

    public void prevPage() { goToPage(params.getPage() - 1); }
    public void nextPage() { goToPage(params.getPage() + 1); }

    /**
     * The compact page-numbers list shown in the React Pagination component:
     * always include first, last, current, and current ± 1 — fill the gaps with -1
     * which the view renders as an ellipsis.
     */
    public List<Integer> getVisiblePages() {
        if (page == null || page.getTotalPages() <= 1) return List.of();
        int totalPages = page.getTotalPages();
        int current = page.getPageNumber();

        List<Integer> visible = new ArrayList<>();
        IntStream.range(0, totalPages)
                .filter(p -> p == 0 || p == totalPages - 1 || Math.abs(p - current) <= 1)
                .forEach(visible::add);

        // Insert -1 markers where there's a gap (corresponds to the React ellipsis).
        List<Integer> out = new ArrayList<>(visible.size() + 2);
        for (int i = 0; i < visible.size(); i++) {
            if (i > 0 && visible.get(i) - visible.get(i - 1) > 1) out.add(-1);
            out.add(visible.get(i));
        }
        return out;
    }

    // ─── Modal: open / close ─────────────────────────────────────────────────

    public void openCreate() {
        modalEditMode = false;
        editingTask = null;
        formTitle = "";
        formDescription = "";
        formStatus = TaskStatus.TODO;
        formDueDate = null;
        modalOpen = true;
        showTaskDialog();
    }

    public void openEdit(Task t) {
        modalEditMode = true;
        editingTask = t;
        formTitle = t.getTitle();
        formDescription = t.getDescription();
        formStatus = t.getStatus();
        formDueDate = t.getDueDate();
        modalOpen = true;
        showTaskDialog();
    }

    public void closeModal() {
        modalOpen = false;
        editingTask = null;
        hideTaskDialog();
    }

    private void showTaskDialog() {
        PrimeFaces pf = PrimeFaces.current();
        if (pf != null) pf.executeScript("PF('taskModalWidget').show()");
    }

    private void hideTaskDialog() {
        PrimeFaces pf = PrimeFaces.current();
        if (pf != null) pf.executeScript("PF('taskModalWidget').hide()");
    }

    private void hideConfirmDialog() {
        PrimeFaces pf = PrimeFaces.current();
        if (pf != null) pf.executeScript("PF('confirmDialogWidget').hide()");
    }

    // ─── Modal: submit ───────────────────────────────────────────────────────

    public void submitForm() {
        submitting = true;
        try {
            TaskPayload payload = new TaskPayload();
            payload.setTitle(formTitle);
            payload.setDescription(blankToNull(formDescription));
            payload.setStatus(formStatus);
            payload.setDueDate(formDueDate);

            if (modalEditMode && editingTask != null) {
                taskService.update(editingTask.getId(), payload);
                toastSuccess("Task updated successfully");
            } else {
                taskService.create(payload);
                toastSuccess("Task created successfully");
            }
            closeModal();
            loadTasks();
        } catch (TaskApiException e) {
            toastError(e.getMessage());
        } finally {
            submitting = false;
        }
    }

    // ─── Status quick-change (the "Move to ↓" menu) ──────────────────────────

    public void changeStatus(Task t, String newStatus) {
        try {
            TaskStatus s = TaskStatus.valueOf(newStatus);
            TaskPayload payload = new TaskPayload();
            payload.setStatus(s);
            taskService.updateStatus(t.getId(), payload);
            toastSuccess("Status updated");
            loadTasks();
        } catch (TaskApiException e) {
            toastError(e.getMessage());
        } catch (IllegalArgumentException e) {
            toastError("Invalid status: " + newStatus);
        }
    }

    // ─── Delete confirmation ─────────────────────────────────────────────────

    public void promptDelete(Task t) {
        this.deletingTask = t;
        PrimeFaces pf = PrimeFaces.current();
        if (pf != null) pf.executeScript("PF('confirmDialogWidget').show()");
    }

    public void cancelDelete() {
        this.deletingTask = null;
        hideConfirmDialog();
    }

    public void confirmDelete() {
        if (deletingTask == null) return;
        try {
            taskService.delete(deletingTask.getId());
            toastSuccess("Task deleted");
            deletingTask = null;
            hideConfirmDialog();
            loadTasks();
        } catch (TaskApiException e) {
            toastError(e.getMessage());
        }
    }

    // ─── Toast helpers (replace react-hot-toast) ─────────────────────────────

    private void toastSuccess(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    private void toastError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // ─── View getters/setters ────────────────────────────────────────────────

    public TaskQueryParams getParams() { return params; }

    public PagedResponse<Task> getPage()   { return page; }
    public List<Task> getTasks()           { return page == null ? List.of() : page.getContent(); }
    public long getTotalElements()         { return page == null ? 0L : page.getTotalElements(); }
    public int getTotalPages()             { return page == null ? 0  : page.getTotalPages(); }
    public int getCurrentPage()            { return params.getPage() == null ? 0 : params.getPage(); }
    public boolean isHasFilters()          { return params.hasFilters(); }
    public boolean isNoTasks()             { return getTasks().isEmpty(); }

    public boolean isLoading()      { return loading; }
    public String  getLoadError()   { return loadError; }
    public boolean isHasError()     { return loadError != null; }

    public boolean isModalOpen()         { return modalOpen; }
    public boolean isModalEditMode()     { return modalEditMode; }
    public boolean isSubmitting()        { return submitting; }
    public boolean isDeleteDialogOpen()  { return deletingTask != null; }
    public Task    getDeletingTask()     { return deletingTask; }

    public String getFormTitle() { return formTitle; }
    public void   setFormTitle(String formTitle) { this.formTitle = formTitle; }

    public String getFormDescription() { return formDescription; }
    public void   setFormDescription(String formDescription) { this.formDescription = formDescription; }

    public TaskStatus getFormStatus() { return formStatus; }
    public void       setFormStatus(TaskStatus formStatus) { this.formStatus = formStatus; }

    /**
     * Form-side representation of the Due Date input.
     *
     * <p>The React app used <code>&lt;input type="datetime-local"&gt;</code> which produces a
     * naive local-time string and then attached the browser's offset on submit
     * (<code>localDateTimeToISO</code>). PrimeFaces' p:datePicker bound to
     * {@link OffsetDateTime} does the equivalent: parsing/displaying in the JVM's
     * default zone and emitting an OffsetDateTime when read.
     */
    public OffsetDateTime getFormDueDate() { return formDueDate; }
    public void           setFormDueDate(OffsetDateTime formDueDate) { this.formDueDate = formDueDate; }

    /** Used by p:datePicker minDate for the Due Date input. */
    public java.time.LocalDateTime getMinDueDate() {
        return java.time.LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(1);
    }

}
