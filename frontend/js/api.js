/* ============================================================================
   api.js — the only place in the frontend that talks to the server.
   ...
   ========================================================================= */
const API = (() => {

  async function call(method, url, body) {
    const options = { method, credentials: 'same-origin' };
    if (body !== undefined) {
      options.headers = { 'Content-Type': 'application/json' };
      options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);

    if (response.status === 204) return null;          // DELETE / logout

    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (ignored) { data = { message: text }; }
    }

    if (!response.ok) {
      const error = new Error((data && data.message) || ('Request failed (' + response.status + ')'));
      error.status  = response.status;
      error.details = (data && data.details) || [];

      // 401 means the session has gone (never logged in, or timed out after
      // 30 minutes). Whatever the user was doing, they must log in again - so
      // send them there instead of leaving a page full of error messages.
      if (response.status === 401 && !window.location.pathname.endsWith('login.html')) {
        window.location.replace('login.html');
      }
      throw error;
    }
    return data;
  }

  return {
    get : (url)        => call('GET',    url),
    post: (url, body)  => call('POST',   url, body),
    put : (url, body)  => call('PUT',    url, body),
    del : (url)        => call('DELETE', url)
  };
})();
