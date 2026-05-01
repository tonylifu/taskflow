/* TaskFlow — small client-side helpers
 * These reproduce the bits of behaviour that lived in the React component
 * tree but have no direct JSF equivalent.
 */

(function () {
    'use strict';

    /**
     * Debounce a remoteCommand call. Mirrors the React TaskFilters
     * `useEffect` that fires 350ms after the user stops typing.
     */
    var debounceTimers = {};
    window.taskflowDebounce = function (key, fn, delay) {
        if (debounceTimers[key]) clearTimeout(debounceTimers[key]);
        debounceTimers[key] = setTimeout(fn, delay || 350);
    };

    /**
     * Toggle the inline status menu on a task card.
     * Mirrors the `showStatusMenu` useState in TaskCard.
     * Closes other open menus when one is opened — only one at a time, like the React UI.
     */
    window.taskflowToggleStatusMenu = function (cardId) {
        document.querySelectorAll('.status-menu.is-open').forEach(function (el) {
            if (el.id !== cardId) el.classList.remove('is-open');
        });
        var menu = document.getElementById(cardId);
        if (menu) menu.classList.toggle('is-open');
    };

    // Click-outside-to-close for status menus.
    document.addEventListener('click', function (ev) {
        if (ev.target.closest('.task-card__status-change')) return;
        document.querySelectorAll('.status-menu.is-open')
                .forEach(function (el) { el.classList.remove('is-open'); });
    });

    // Close modal on Escape — mirrors the TaskModal useEffect.
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Escape') return;
        if (window.PF && window.taskModalWidget && window.taskModalWidget.isVisible()) {
            window.taskModalWidget.hide();
        }
        if (window.PF && window.confirmDialogWidget && window.confirmDialogWidget.isVisible()) {
            window.confirmDialogWidget.hide();
        }
    });
})();
