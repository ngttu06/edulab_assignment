(() => {
    'use strict';
    document.addEventListener('DOMContentLoaded', () => {
        const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
        if (!('IntersectionObserver' in window) || preference.matches) return;

        const selector = '.home-discovery > .panel, .community-page > .panel, .community-feed > .feed-card, .course-grid > .course-card';
        const pending = new Set();
        const show = element => {
            if (!pending.delete(element)) return;
            observer.unobserve(element);
            element.classList.add('motion-visible');
            // Remove temporary classes after the transition so hover/focus styles work normally.
            window.setTimeout(() => element.classList.remove('motion-pending', 'motion-visible'), 520);
        };
        const observer = new IntersectionObserver(entries => {
            for (const entry of entries) if (entry.isIntersecting) show(entry.target);
        }, {rootMargin: '0px 0px 60px 0px', threshold: 0.01});

        const watch = element => {
            if (preference.matches || pending.has(element) || !element.isConnected) return;
            // Never conceal content already in view or the target of keyboard navigation.
            if (element.getBoundingClientRect().top < window.innerHeight + 40 || element.contains(document.activeElement)) return;
            element.classList.add('motion-pending');
            pending.add(element);
            observer.observe(element);
        };
        document.querySelectorAll(selector).forEach(watch);
        document.addEventListener('focusin', event => {
            const element = event.target.closest(selector);
            if (element) show(element);
        });
        preference.addEventListener('change', () => {
            if (preference.matches) {
                for (const element of [...pending]) show(element);
                observer.disconnect();
            } else {
                document.querySelectorAll(selector).forEach(watch);
            }
        });

        // The community feed appends cards without navigating; observe only that container.
        const feed = document.getElementById('communityFeed');
        if (feed) new MutationObserver(records => {
            for (const record of records) for (const node of record.addedNodes) {
                if (node.nodeType === Node.ELEMENT_NODE && node.matches('.feed-card')) watch(node);
            }
        }).observe(feed, {childList: true});
    });
})();