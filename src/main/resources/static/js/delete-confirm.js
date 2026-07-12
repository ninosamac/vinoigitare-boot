/**
 * Confirmation prompt for any dangerous form submission (currently: admin
 * song delete, on both admin/list.html and song.html). Thymeleaf rejects
 * free-form string expressions in th:onsubmit ("Only variable expressions
 * returning numbers or booleans are allowed in this context" -- an XSS
 * guard), so the confirmation message travels via a data-* attribute and
 * is read from here instead, per that restriction's own suggested fix.
 */
(function () {
    "use strict";

    document.querySelectorAll("[data-delete-form]").forEach(function (form) {
        form.addEventListener("submit", function (event) {
            if (!window.confirm(form.getAttribute("data-confirm-delete"))) {
                event.preventDefault();
            }
        });
    });
})();
