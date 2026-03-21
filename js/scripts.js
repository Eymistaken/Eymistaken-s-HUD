document.addEventListener('DOMContentLoaded', () => {

    // ── Scroll Reveal ─────────────────────────────────────────
    const REVEAL_SELECTORS = [
        // Generic sections & wrappers
        'main > section',
        'main > div.api-section',
        'main > div.bg-surf',
        'main > div.callout',
        // Cards & grids — stagger siblings
        '.module-card',
        '.grid > div',
        '.grid > a',
        // Page header content
        '.dot-grid .max-w-7xl > *',
        // Stats bar children
        '.border-y .max-w-7xl > div',
        // Feature section cards
        '.py-24 .grid > div',
        // Downloads cards
        '.grid.grid-cols-1.md\\:grid-cols-3 > a',
        // Installation / recommendation blocks
        '.border-l-2',
        '.border-l-4',
        // Keystrokes designer section
        '#keystrokes-designer .bg-surf',
        // Context menu section
        '.mb-16 .grid',
        // Footer
        'footer',
    ];

    const revealEls = new Set();
    REVEAL_SELECTORS.forEach(sel => {
        try {
            document.querySelectorAll(sel).forEach(el => revealEls.add(el));
        } catch(e) {}
    });

    // Also reveal individual api-section children (headings, paragraphs, pre blocks)
    document.querySelectorAll('.api-section').forEach(section => {
        revealEls.add(section);
    });

    // Apply .reveal class and stagger delay within sibling groups
    revealEls.forEach(el => {
        // Skip elements that are already faded-up via inline animation-delay
        if (el.classList.contains('fade-up')) return;
        // Stagger: find position among revealed siblings
        const siblings = el.parentElement
            ? Array.from(el.parentElement.children).filter(c => revealEls.has(c))
            : [];
        const idx = siblings.indexOf(el);
        el.classList.add('reveal');
        if (idx > 0) el.style.transitionDelay = (idx * 0.04) + 's';
    });

    const revealObserver = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
                revealObserver.unobserve(entry.target); // fire once only
            }
        });
    }, { threshold: 0.01, rootMargin: '0px 0px 120px 0px' });

    revealEls.forEach(el => {
        if (!el.classList.contains('fade-up')) revealObserver.observe(el);
    });

    // Trigger elements already in viewport on page load
    requestAnimationFrame(() => {
        revealEls.forEach(el => {
            if (el.classList.contains('fade-up')) return;
            const rect = el.getBoundingClientRect();
            if (rect.top < window.innerHeight && rect.bottom > 0) {
                el.classList.add('visible');
                revealObserver.unobserve(el);
            }
        });
    });


    const page = window.location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('nav a[data-page]').forEach(link => {
        if (link.dataset.page === page) link.classList.add('nav-active');
    });

    // ── Copy Buttons on <pre> blocks ─────────────────────────
    document.querySelectorAll('pre').forEach(pre => {
        const btn = document.createElement('button');
        btn.className = 'copy-btn';
        btn.textContent = 'COPY';
        pre.style.position = 'relative';
        pre.appendChild(btn);
        btn.addEventListener('click', () => {
            const code = pre.querySelector('code');
            if (!code) return;
            navigator.clipboard.writeText(code.innerText).then(() => {
                btn.textContent = 'COPIED';
                setTimeout(() => (btn.textContent = 'COPY'), 2000);
            });
        });
    });

    // ── Module Modal — handled inline in user-guide.html ─────

    // ── Dynamic version badge (api-guide) ────────────────────
    const versionEl = document.getElementById('dynamic-version');
    if (versionEl) {
        fetch('https://api.github.com/repos/Eymistaken/Eymistaken-s-HUD/releases/latest')
            .then(r => r.ok ? r.json() : null)
            .then(d => { if (d?.tag_name) versionEl.textContent = d.tag_name; })
            .catch(() => {});
    }
});
