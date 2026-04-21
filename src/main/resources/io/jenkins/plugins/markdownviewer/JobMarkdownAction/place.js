/*
 * Markdown Viewer — jobMain.jelly repositioner.
 *
 * Loaded as an adjunct from jobMain.jelly. Job main.jelly iterates actions in
 * registration order, so our markdown summary box normally ends up below the
 * Pipeline Stage View. This script finds our single div (by id) and moves it
 * immediately below Jenkins' "Last Successful Artifacts" table. It only
 * touches our own element; no DOM observers, no links rewrite.
 */
(function () {
    'use strict';

    function place() {
        var box = document.getElementById('markdown-viewer-job-summary');
        if (!box) return;

        var anchor = document.querySelector('a[href*="lastSuccessfulBuild/artifact/"]');
        var target = null;
        if (anchor) {
            var t = anchor;
            while (t && t.tagName !== 'TABLE') {
                t = t.parentElement;
            }
            target = t;
        }

        if (!target) {
            target = document.querySelector('#main-panel table')
                  || document.querySelector('#main-panel h2');
        }

        if (target && target.parentNode && box !== target.nextSibling) {
            target.parentNode.insertBefore(box, target.nextSibling);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', place);
    } else {
        place();
    }
})();
