# TaskFlow — JSF / PrimeFaces 15 Frontend

A direct conversion of the React + TypeScript `task-manager-frontend` app to
**Jakarta EE 11 + JSF 4.1 + PrimeFaces 15**, preserving the exact look and feel
and all functionality.

The original CSS design system (DM Serif Display + DM Sans, dark slate /
amber-accent palette, all 689 lines of `index.css`) is shipped verbatim;
PrimeFaces' default chrome is overridden in `primefaces-overrides.css` so the
widgets blend in seamlessly.

---

## Stack

| Layer        | Technology                                       |
|--------------|--------------------------------------------------|
| View         | Facelets (`*.xhtml`) + PrimeFaces 15 (jakarta)   |
| Backing beans| CDI `@ViewScoped` + `@ApplicationScoped`         |
| HTTP client  | Jersey 3.1 (JAX-RS Client)                       |
| JSON         | Jackson 2.18 + jsr310 module                     |
| Server       | WildFly 36+ (Jakarta EE 11), Java 21+            |

---

## Build & run

```bash
mvn clean package
# → target/taskflow.war

# Drop into WildFly:
cp target/taskflow.war $JBOSS_HOME/standalone/deployments/
```

The app is served at `http://localhost:8080/taskflow/tasks.xhtml`
(or `http://localhost:8080/taskflow/` thanks to the welcome-file mapping).

### Backend API

The frontend expects the same Tasks REST API the React app talked to,
exposing `GET / POST / PUT / PATCH / DELETE /api/v1/tasks[/{id}[/status]]`
with the `ApiResponse<T>` / `PagedResponse<T>` envelopes.

Configure the base URL via either:

- system property `-Dtaskflow.api.baseUrl=https://api.example.com/api/v1`, or
- env var `TASKFLOW_API_BASE_URL=https://api.example.com/api/v1`

Default: `http://localhost:8080/api/v1`.

---

## Architecture mapping

The conversion is one-to-one wherever practical:

| React (original)                  | JSF (this project)                                  |
|-----------------------------------|-----------------------------------------------------|
| `App.tsx`                         | `tasks.xhtml`                                       |
| `Header.tsx`                      | inline header in `tasks.xhtml`                      |
| `TaskList.tsx`                    | `tasks.xhtml` body + `TaskListBean`                 |
| `TaskCard.tsx`                    | `WEB-INF/includes/taskCard.xhtml`                   |
| `TaskModal.tsx` + `TaskForm.tsx`  | `WEB-INF/includes/taskModal.xhtml`                  |
| `TaskFilters.tsx`                 | inline `.filters` block in `tasks.xhtml`            |
| `ConfirmDialog.tsx`               | `WEB-INF/includes/confirmDialog.xhtml`              |
| `Pagination.tsx`                  | inline `.pagination` block in `tasks.xhtml`         |
| `EmptyState.tsx`                  | inline `.empty-state` block in `tasks.xhtml`        |
| `LoadingSpinner.tsx`              | inline `.spinner-wrapper` markup                    |
| `StatusBadge.tsx`                 | inline `.badge` span (uses `TaskStatus.badgeClass`) |
| `useTasks` / `useCreateTask` / …  | methods on `TaskListBean` (single ViewScoped bean)  |
| `taskService.ts` + `apiClient.ts` | `TaskService` (Jersey JAX-RS client, CDI)           |
| `dateUtils.ts`                    | `DateUtil` + `ViewHelper` (EL: `#{view.…}`)         |
| `types/index.ts`                  | `model/TaskStatus`, `model/Task`, `dto/*`           |
| `react-hot-toast` (Toaster)       | `<p:growl>` (PrimeFaces growl)                      |

State that lived in `useState` (filter params, the open modal, the task
being edited or deleted, submitting flag, etc.) lives as fields on the
ViewScoped `TaskListBean`. Each AJAX action mutates these fields exactly
the way the React handlers mutate `useState` and re-renders only the
relevant subtree (`render=":form:taskList"` etc.).

---

## Behaviour parity checklist

- ✅ Sticky header with logo + active "Tasks" nav
- ✅ Page title (DM Serif Display, clamp 2–3rem) + subtitle
- ✅ Search input — debounced 350 ms (`taskflowDebounce` in `taskflow.js`)
- ✅ Status filter (All / TODO / In Progress / On Hold / Done / Cancelled)
- ✅ Sort direction (Newest first / Oldest first)
- ✅ Active-filter detection → "Clear filters" button + adjusted count text
- ✅ Empty state with icon + contextual CTA
- ✅ Card grid (DONE state styling, due-date pill with overdue/soon variants)
- ✅ Edit / delete actions on each card
- ✅ "Move to ↓" inline status menu (one open at a time, click-outside closes)
- ✅ Pagination with first/last + current ± 1 + ellipsis
- ✅ Create / edit modal — Title + Description + Status + Due Date with validation
- ✅ Min-date check (Due date must be in the future)
- ✅ Delete confirmation dialog
- ✅ Toast / growl on every mutation success/failure
- ✅ Escape closes modal & confirm dialog

---

## Files

```
src/main/
├── java/com/taskflow/
│   ├── bean/
│   │   ├── TaskListBean.java        ← @ViewScoped, all page orchestration
│   │   └── ViewHelper.java          ← @Named("view"), EL helpers
│   ├── dto/
│   │   ├── ApiResponse.java         ← matches the React ApiResponse<T>
│   │   ├── PagedResponse.java       ← matches the React PagedResponse<T>
│   │   ├── TaskPayload.java         ← matches Create/UpdateTaskPayload
│   │   └── TaskQueryParams.java     ← matches TaskQueryParams
│   ├── model/
│   │   ├── Task.java
│   │   └── TaskStatus.java          ← enum w/ label + badgeClass
│   ├── service/
│   │   ├── TaskService.java         ← JAX-RS client, mirrors taskService.ts
│   │   └── TaskApiException.java
│   └── util/
│       └── DateUtil.java            ← mirrors dateUtils.ts
└── webapp/
    ├── WEB-INF/
    │   ├── beans.xml
    │   ├── web.xml
    │   └── includes/
    │       ├── confirmDialog.xhtml
    │       ├── taskCard.xhtml
    │       └── taskModal.xhtml
    ├── resources/
    │   ├── css/
    │   │   ├── primefaces-overrides.css   ← PF chrome → design system
    │   │   └── taskflow.css                ← original 689-line stylesheet, verbatim
    │   └── js/
    │       └── taskflow.js                 ← debounce, status menu, ESC close
    └── tasks.xhtml                          ← the page
```
