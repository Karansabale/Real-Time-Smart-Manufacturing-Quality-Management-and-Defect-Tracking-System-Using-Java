/* ============================================================================
   layout.js — session handling, the navbar, and the small display helpers.
   ...
   ========================================================================= */
const Layout = (() => {

  const ALL_ROLES = ['ADMIN', 'INSPECTOR', 'SUPERVISOR'];

  /* The menu, in the order given in the design (§10.2). 'roles' is the
     permission matrix from Phase 4 §6.2, expressed in the interface. */
  const MENU = [
    { href: 'dashboard.html',         label: 'Dashboard',         roles: ALL_ROLES },
    { href: 'products.html',          label: 'Products',          roles: ALL_ROLES },
    { href: 'batches.html',           label: 'Batches',           roles: ALL_ROLES },
    { href: 'inspections.html',       label: 'Inspections',       roles: ALL_ROLES },
    { href: 'defects.html',           label: 'Defects',           roles: ALL_ROLES },
    { href: 'corrective-actions.html',label: 'Corrective Actions',roles: ALL_ROLES },
    { href: 'reports.html',           label: 'Reports',           roles: ALL_ROLES },
    { href: 'users.html',             label: 'Users',             roles: ['ADMIN'] }
  ];

  const ROLE_LABEL = {
    ADMIN: 'Administrator',
    INSPECTOR: 'Quality Inspector',
    SUPERVISOR: 'Production Supervisor'
  };

  let me = null;

  /* Called at the top of every page. Returns the logged-in user, or null
     after sending the browser to the login screen. */
  async function boot() {
    try {
      me = await API.get('/api/auth/me');
    } catch (error) {
      window.location.replace('login.html');          // not signed in, or session expired
      return null;
    }
    renderNavbar();
    return me;
  }

  function renderNavbar() {
    const here = window.location.pathname.split('/').pop() || 'index.html';
    const items = MENU
      .filter(item => item.roles.includes(me.role))
      .map(item => '<li class="nav-item"><a class="nav-link' + (item.href === here ? ' active' : '') +
                   '" href="' + item.href + '">' + item.label + '</a></li>')
      .join('');

    document.getElementById('app-navbar').innerHTML =
      '<nav class="navbar navbar-expand-lg navbar-dark bg-dark">' +
      '  <div class="container-fluid">' +
      '    <a class="navbar-brand fw-semibold" href="dashboard.html">Manufacturing QMS</a>' +
      '    <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navmenu">' +
      '      <span class="navbar-toggler-icon"></span></button>' +
      '    <div class="collapse navbar-collapse" id="navmenu">' +
      '      <ul class="navbar-nav me-auto">' + items + '</ul>' +
      '      <span class="navbar-text small me-3">' + esc(me.fullName) + ' &middot; ' + ROLE_LABEL[me.role] + '</span>' +
      '      <button class="btn btn-outline-light btn-sm" id="btn-logout">Log out</button>' +
      '    </div></div></nav>';

    document.getElementById('btn-logout').addEventListener('click', logout);
  }

  async function logout() {
    try { await API.post('/api/auth/logout'); } catch (ignored) { /* leaving anyway */ }
    window.location.replace('login.html');
  }

  /* ---------- display helpers, shared by every screen -------------------- */

  function esc(value) {
    return String(value === null || value === undefined ? '' : value)
      .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
  }

  /* A single stock phrase, because the same sentence in six files would
     eventually be six different sentences. */
  function statusBadge(status) {
    if (!status) return '';
    return '<span class="badge status-badge st-' + esc(status) + '">' + esc(status.replaceAll('_', ' ')) + '</span>';
  }
  function severityBadge(severity) {
    if (!severity) return '';
    return '<span class="badge sev-badge sev-' + esc(severity) + '">' + esc(severity) + '</span>';
  }

  function date(iso) {
    if (!iso) return '&mdash;';
    const d = new Date(iso);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  }
  function dateTime(iso) {
    if (!iso) return '&mdash;';
    const d = new Date(iso);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' +
           d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  }

  /* Messages always appear at the top of the content area (§10.2). */
  function showMessage(html, type) {
    const box = document.getElementById('message');
    if (box) box.innerHTML = '<div class="alert alert-' + (type || 'success') + ' alert-dismissible fade show">' +
      html + '<button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>';
  }
  function clearMessage() {
    const box = document.getElementById('message');
    if (box) box.innerHTML = '';
  }

  /* One error handler for the whole application. The API always answers with
     the same ApiError shape (FR-12.8), so errors are displayed the same way
     everywhere: the message, then any field-level details. */
  function showError(error) {
    let html = esc(error.message || 'Something went wrong.');
    if (error.details && error.details.length) {
      html += '<ul class="mb-0 mt-1">' + error.details.map(d => '<li>' + esc(d) + '</li>').join('') + '</ul>';
    }
    if (error.status === 500) html = esc(error.message) + ' Please check the server log.';
    showMessage(html, 'danger');
  }

  /* Marks a table body that has no rows with the standard empty state,
     instead of leaving a blank block (FR-10.11). */
  function emptyRow(tbody, columnCount, message) {
    tbody.innerHTML = '<tr><td colspan="' + columnCount + '">' +
      '<div class="empty-state"><div class="empty-title">' + esc(message || 'No records found for the selected criteria.') + '</div>' +
      '<div>Adjust the filters above and try again.</div></div></td></tr>';
  }

  /* Fills a <select> with options: [{value, label}, ...] */
  function fillSelect(select, options, selectedValue, placeholder) {
    let html = placeholder ? '<option value="">' + esc(placeholder) + '</option>' : '';
    html += options.map(o => '<option value="' + esc(o.value) + '"' +
      (String(o.value) === String(selectedValue) ? ' selected' : '') + '>' + esc(o.label) + '</option>').join('');
    select.innerHTML = html;
  }

  /* A small table helper used by every list screen, so pagination and the
     empty state are written once instead of six times.

     Usage:  const table = Layout.table('rows', 'pager-footer', 9);
             table.setRows(arrayOfRowHtmlStrings);
     The page builds one <tr>...</tr> string per record and hands them over;
     showing a page, counting the records and drawing the pager is done here. */
  function table(rowsId, footerId, columnCount, emptyMessage) {
    const PAGE_SIZE = 15;
    let all = [];
    let page = 1;

    function render() {
      const tbody = document.getElementById(rowsId);
      const footer = document.getElementById(footerId);

      if (all.length === 0) {
        emptyRow(tbody, columnCount, emptyMessage);
        if (footer) footer.innerHTML = '';
        return;
      }

      const pages = Math.max(1, Math.ceil(all.length / PAGE_SIZE));
      if (page > pages) page = pages;
      const start = (page - 1) * PAGE_SIZE;
      tbody.innerHTML = all.slice(start, start + PAGE_SIZE).join('');

      if (footer) {
        footer.innerHTML =
          '<span class="text-secondary small">Showing ' + (start + 1) + '&ndash;' +
          Math.min(start + PAGE_SIZE, all.length) + ' of ' + all.length + ' record' +
          (all.length === 1 ? '' : 's') + '</span>' +
          (pages > 1
            ? '<span class="btn-group btn-group-sm">' +
              '<button type="button" class="btn btn-outline-secondary" id="pg-prev"' + (page === 1 ? ' disabled' : '') + '>Previous</button>' +
              '<button type="button" class="btn btn-outline-secondary" disabled>Page ' + page + ' of ' + pages + '</button>' +
              '<button type="button" class="btn btn-outline-secondary" id="pg-next"' + (page === pages ? ' disabled' : '') + '>Next</button></span>'
            : '');
        const prev = document.getElementById('pg-prev');
        const next = document.getElementById('pg-next');
        if (prev) prev.addEventListener('click', () => { page--; render(); });
        if (next) next.addEventListener('click', () => { page++; render(); });
      }
    }

    return {
      setRows(rows) { all = rows; page = 1; render(); },
      get rows() { return all; },
      render
    };
  }

  return {
    boot, logout, esc, statusBadge, severityBadge, date, dateTime,
    showMessage, clearMessage, showError, emptyRow, fillSelect, table,
    get user() { return me; },
    roleLabel: (role) => ROLE_LABEL[role] || role
  };
})();
