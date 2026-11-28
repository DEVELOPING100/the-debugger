/* TA page: open a lab by its join code, work the queue (SCRUM-7),
   and answer similar questions all at once (SCRUM-10, SCRUM-11). */
(() => {
  const REFRESH_MS = 2000;               // NFR2: refresh every 2 seconds
  const SESSION_KEY = "labline.ta.joinCode";
  const MAX_ANSWER = 230;                // same limit as TaQueueService.MAX_ANSWER_LENGTH
  const user = LabLine.requireLogin(["TA", "INSTRUCTOR"]);
  if (!user) { return; }

  const el = (id) => document.getElementById(id);
  const message = el("message");
  let session = null;   // SessionInfo from the server
  let queue = [];       // open requests (HelpRequestResponse), oldest first
  let busy = false;     // true while an action runs, so a button can't send it twice
  let timer = null;

  el("user-name").textContent = user.name;
  el("logout").addEventListener("click", LabLine.logout);

  // ---------- opening a lab ----------

  el("join-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await openSession(el("join-code").value);
  });

  el("leave-session").addEventListener("click", () => {
    LabLine.write(SESSION_KEY, null);
    session = null;
    queue = [];
    stopRefresh();
    clearGroups();
    render();
  });

  async function openSession(code) {
    try {
      session = await LabLine.api("GET", "/api/sessions/join/" + encodeURIComponent(code.trim()));
      LabLine.write(SESSION_KEY, code.trim());
      LabLine.showMessage(message, "", "error");
      await refreshQueue();
      startRefresh();
    } catch (error) {
      session = null;
      LabLine.showMessage(message, error.message, "error");
      render();
    }
  }

  // ---------- the queue, refreshed every 2 s ----------

  async function refreshQueue() {
    if (!session) { return; }
    try {
      queue = (await LabLine.api("GET", "/api/ta/sessions/" + session.id + "/queue")) || [];
      render();
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  }

  function startRefresh() {
    stopRefresh();
    timer = setInterval(refreshQueue, REFRESH_MS);
  }

  function stopRefresh() {
    if (timer) { clearInterval(timer); timer = null; }
  }

  // ---------- TA actions (SCRUM-7) ----------

  el("claim-next").addEventListener("click", () =>
      act("/api/ta/sessions/" + session.id + "/claim-next",
          (claimed) => "You're helping the student at " + claimed.seat + "."));

  /** A button handler that sends one action (resolve, defer or return) for one request. */
  function requestAction(request, action, doneText) {
    return () => act("/api/ta/requests/" + request.id + "/" + action, () => doneText);
  }

  /** Sends one action, shows the result, then redraws the queue. Returns true if it worked. */
  async function act(path, successText, body) {
    if (busy) { return false; }
    busy = true;
    render();
    try {
      const result = await LabLine.api("POST", path, body);
      LabLine.showMessage(message, successText(result), "success");
      return true;
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
      return false;
    } finally {
      busy = false;
      await refreshQueue();
    }
  }

  // ---------- similar questions and one answer for all (SCRUM-10, SCRUM-11) ----------

  el("find-groups").addEventListener("click", findGroups);

  async function findGroups() {
    try {
      const clusters = (await LabLine.api("GET", "/api/ta/sessions/" + session.id + "/clusters")) || [];
      const groups = clusters.filter((cluster) => cluster.requests.length > 1);
      el("groups-list").replaceChildren(...groups.map(groupBox));
      el("groups-empty").classList.toggle("hidden", groups.length > 0);
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  }

  function clearGroups() {
    el("groups-list").replaceChildren();
    el("groups-empty").classList.add("hidden");
  }

  async function broadcast(cluster, answer) {
    if (!answer.trim()) {
      LabLine.showMessage(message, "Write the answer first.", "error");
      return;
    }
    const sent = await act("/api/ta/sessions/" + session.id + "/broadcast",
        (resolved) => "Answered " + resolved.length + " students at once.",
        { requestIds: cluster.requests.map((request) => request.id), answer });
    if (sent) { clearGroups(); }
  }

  // ---------- drawing the page ----------

  function render() {
    const open = session !== null;
    el("join-card").classList.toggle("hidden", open);
    ["session-card", "queue-card", "groups-card"].forEach((id) =>
        el(id).classList.toggle("hidden", !open));
    if (!open) { return; }

    el("session-title").textContent = session.courseCode + " · " + session.name;
    el("session-room").textContent = session.room ? "Room " + session.room : "";
    el("queue-chip").textContent = session.queueOpen ? "QUEUE OPEN" : "QUEUE CLOSED";
    el("queue-chip").className = "chip " + (session.queueOpen ? "OPEN" : "CLOSED");

    const waiting = queue.filter((request) => request.status === "WAITING").length;
    el("waiting-count").textContent = waiting;
    el("claim-next").disabled = busy || waiting === 0;
    el("queue-empty").classList.toggle("hidden", queue.length > 0);
    el("queue-list").replaceChildren(...queue.map(queueItem));
  }

  /** One card in the queue: seat, question, status, and the buttons this TA may use. */
  function queueItem(request) {
    const mine = request.claimedById === user.id;
    const item = document.createElement("li");
    item.className = "queue-item" + (mine ? " mine" : "");

    const head = document.createElement("div");
    head.className = "queue-item-head";
    head.append(text("strong", request.seat), statusChip(request.status, mine));
    item.append(head,
        text("p", request.description),
        text("p", categoryName(request.categoryId) + " · joined " + timeAgo(request.timeJoined), "muted"));

    const buttons = buttonsFor(request, mine);
    if (buttons.length > 0) {
      const row = document.createElement("div");
      row.className = "row queue-actions";
      row.append(...buttons);
      item.append(row);
    }
    return item;
  }

  /** SCRUM-7 rules: resolve and defer only your own claim; any TA may return a request. */
  function buttonsFor(request, mine) {
    const returnButton = button("Return to queue", "btn-secondary",
        requestAction(request, "return", "Returned to the queue in its original place."));
    if (request.status === "CLAIMED" && mine) {
      return [
        button("Resolved", "btn-primary", requestAction(request, "resolve", "Marked as resolved.")),
        button("Defer", "btn-secondary",
            requestAction(request, "defer", "Deferred. Return it to the queue when the student is ready.")),
        returnButton,
      ];
    }
    if (request.status === "CLAIMED" || request.status === "DEFERRED") {
      return [returnButton];
    }
    return [];
  }

  /** One group of similar questions with an "Answer all" box. */
  function groupBox(cluster) {
    const box = document.createElement("div");
    box.className = "cluster";

    const list = document.createElement("ul");
    list.className = "details";
    cluster.requests.forEach((request) =>
        list.append(text("li", request.seat + " – " + request.description)));

    const answer = document.createElement("textarea");
    answer.maxLength = MAX_ANSWER;
    answer.placeholder = "One answer for all " + cluster.requests.length + " students";
    const counter = text("div", "0 / " + MAX_ANSWER, "counter");
    answer.addEventListener("input", () => {
      counter.textContent = answer.value.length + " / " + MAX_ANSWER;
    });

    const send = button("Answer all " + cluster.requests.length, "btn-primary",
        () => broadcast(cluster, answer.value));
    box.append(text("h3", cluster.label ? "About: " + cluster.label : "Similar questions"),
        list, answer, counter, send);
    return box;
  }

  // ---------- small helpers (textContent only, so student text can never become HTML) ----------

  function text(tag, content, className) {
    const node = document.createElement(tag);
    node.textContent = content;
    if (className) { node.className = className; }
    return node;
  }

  function button(label, className, onClick) {
    const node = text("button", label, className);
    node.type = "button";
    node.disabled = busy;
    node.addEventListener("click", onClick);
    return node;
  }

  function statusChip(status, mine) {
    const label = status === "CLAIMED" ? (mine ? "HELPING" : "OTHER TA") : status;
    return text("span", label, "chip " + status);
  }

  function categoryName(categoryId) {
    const category = session.categories.find((each) => each.id === categoryId);
    return category ? category.name : "Other";
  }

  function timeAgo(isoTime) {
    const minutes = Math.floor((Date.now() - new Date(isoTime).getTime()) / 60000);
    return minutes < 1 ? "just now" : minutes + " min ago";
  }

  // Reopen the last lab automatically after a page reload.
  const savedCode = LabLine.read(SESSION_KEY);
  if (savedCode) { openSession(savedCode); } else { render(); }
})();
