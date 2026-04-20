/* Markdown Viewer Plugin — client-side enhancements.
 *
 * Runs AFTER the server-side sanitized HTML is rendered. It:
 *   1) Applies highlight.js to all <pre><code> blocks except mermaid.
 *   2) Converts <pre><code class="language-mermaid"> blocks to Mermaid diagrams.
 *   3) Builds a floating TOC from h1..h4 headings.
 *   4) Rewrites .md links to keep navigation inside the viewer.
 *
 * This script is intentionally small and dependency-light; hljs + mermaid are
 * loaded as separate <script> tags by the Jelly view.
 */
(function () {
    'use strict';

    function onReady(fn) {
        if (document.readyState !== 'loading') fn();
        else document.addEventListener('DOMContentLoaded', fn);
    }

    function highlightBlocks(root) {
        if (typeof window.hljs === 'undefined') return;
        root.querySelectorAll('pre code').forEach(function (el) {
            if (el.classList.contains('language-mermaid')) return;
            try { window.hljs.highlightElement(el); } catch (e) { /* ignore */ }
        });
    }

    function renderMermaid(root) {
        if (typeof window.mermaid === 'undefined') return;
        var blocks = root.querySelectorAll('pre code.language-mermaid');
        if (!blocks.length) return;
        var theme = document.body.classList.contains('jenkins-!-dark-theme') ? 'dark' : 'default';
        window.mermaid.initialize({ startOnLoad: false, theme: theme, securityLevel: 'strict' });
        blocks.forEach(function (code, i) {
            var pre = code.parentElement;
            var div = document.createElement('div');
            div.className = 'mermaid';
            div.textContent = code.textContent;
            pre.replaceWith(div);
        });
        try { window.mermaid.run({ nodes: root.querySelectorAll('.mermaid') }); } catch (e) { /* ignore */ }
    }

    function buildTOC(root) {
        var headings = root.querySelectorAll('h1, h2, h3, h4');
        if (headings.length < 3) return;
        var main = document.querySelector('#main-panel, #main, .main-panel') || root.parentElement;
        if (!main) return;

        var aside = document.createElement('aside');
        aside.className = 'mdv-toc';
        aside.innerHTML = '<h4>Contents</h4><ul></ul>';
        var ul = aside.querySelector('ul');
        headings.forEach(function (h, idx) {
            if (!h.id) h.id = 'mdv-h-' + idx;
            var li = document.createElement('li');
            li.style.paddingLeft = ((parseInt(h.tagName.substring(1), 10) - 1) * 10) + 'px';
            var a = document.createElement('a');
            a.href = '#' + h.id;
            a.textContent = h.textContent;
            li.appendChild(a);
            ul.appendChild(li);
        });

        // Two-column layout: wrap article in flex container with TOC on the right.
        var article = root;
        var wrapper = document.createElement('div');
        wrapper.style.display = 'grid';
        wrapper.style.gridTemplateColumns = 'minmax(0, 1fr) 220px';
        wrapper.style.gap = '16px';
        article.parentNode.insertBefore(wrapper, article);
        wrapper.appendChild(article);
        wrapper.appendChild(aside);
    }

    function rewriteMdLinks(root) {
        root.querySelectorAll('a[href]').forEach(function (a) {
            var href = a.getAttribute('href');
            if (!href) return;
            if (/^[a-z]+:/i.test(href) || href.startsWith('#') || href.startsWith('/')) return;
            if (href.toLowerCase().endsWith('.md')) {
                a.setAttribute('href', 'show?path=' + encodeURIComponent(href));
            }
        });
    }

    onReady(function () {
        var article = document.querySelector('.mdv-article');
        if (!article) return;
        highlightBlocks(article);
        renderMermaid(article);
        rewriteMdLinks(article);
        buildTOC(article);
    });
})();
