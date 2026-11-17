const siteHeader = document.querySelector('.site-header');
if (siteHeader) {
    const path = window.location.pathname.replace(/\/$/, '') || '/';
    for (const link of siteHeader.querySelectorAll('a[href^="/"]')) {
        const target = link.getAttribute('href');
        const active = path === target || (target !== '/' && path.startsWith(target + '/'));
        link.classList.toggle('is-current', active);
        if (active) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
    }
    const menu = siteHeader.querySelector('.mobile-navigation');
    if (menu) {
        menu.addEventListener('keydown', event => {
            if (event.key === 'Escape' && menu.open) {
                menu.open = false;
                menu.querySelector('summary').focus();
                event.preventDefault();
            }
        });
    }
}
