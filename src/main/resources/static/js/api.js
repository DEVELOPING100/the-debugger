/*
 * LabLine shared API helper. Every page loads this file first.
 *
 *   await LabLine.api("GET", "/api/auth/me")        -> parsed JSON (or null for 204)
 *   await LabLine.api("POST", "/api/requests", body) -> throws Error(message) on 4xx/5xx
 *
 * Login uses HTTP Basic: the email and password are kept for this browser tab only
 * (sessionStorage), and sent with every API call. Closing the tab logs you out.
 */
const LabLine = (() => {
  const AUTH_KEY = "labline.auth";
  const USER_KEY = "labline.user";

  function read(key) {
    try { return sessionStorage.getItem(key); } catch (e) { return null; }
  }

  function write(key, value) {
    try {
      if (value === null) { sessionStorage.removeItem(key); } else { sessionStorage.setItem(key, value); }
    } catch (e) { /* storage blocked: the user just has to log in again */ }
  }

  function basicToken(email, password) {
    return "Basic " + btoa(unescape(encodeURIComponent(email + ":" + password)));
  }

  async function api(method, path, body, token) {
    const headers = { "Accept": "application/json" };
    const auth = token || read(AUTH_KEY);
    if (auth) { headers["Authorization"] = auth; }
    if (body !== undefined) { headers["Content-Type"] = "application/json"; }

    const response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (response.status === 401 && !token) {
      logout();
      return null;
    }
    if (response.status === 204) { return null; }

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const message = (data && data.error) || ("Request failed (" + response.status + ")");
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    return data;
  }

  /** Checks the email and password with the server, then remembers them. */
  async function login(email, password) {
    const token = basicToken(email.trim(), password);
    try {
      const user = await api("GET", "/api/auth/me", undefined, token);
      write(AUTH_KEY, token);
      write(USER_KEY, JSON.stringify(user));
      return user;
    } catch (error) {
      if (error.status === 401) { throw new Error("Wrong email or password"); }
      throw error;
    }
  }

  function register(email, name, password) {
    return api("POST", "/api/auth/register", { email: email.trim(), name: name.trim(), password });
  }

  function currentUser() {
    const saved = read(USER_KEY);
    return saved ? JSON.parse(saved) : null;
  }

  function logout() {
    write(AUTH_KEY, null);
    write(USER_KEY, null);
    window.location.href = "index.html";
  }

  /** Call at the top of a page: sends the user to the login page if needed. */
  function requireLogin(allowedRoles) {
    const user = currentUser();
    if (!user) { window.location.href = "index.html"; return null; }
    if (allowedRoles && !allowedRoles.includes(user.role)) {
      window.location.href = homePageFor(user.role);
      return null;
    }
    return user;
  }

  /** Where each role lands after logging in. */
  function homePageFor(role) {
    return (role === "TA" || role === "INSTRUCTOR") ? "ta.html" : "student.html";
  }

  /** Shows a message in an element; type is "error" or "success". */
  function showMessage(element, text, type) {
    element.textContent = text;
    element.className = "message " + type;
    element.classList.toggle("hidden", !text);
  }

  return { api, login, register, logout, currentUser, requireLogin, homePageFor, showMessage, read, write };
})();
