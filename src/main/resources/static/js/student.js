/* Student page: join a lab (FR4), submit (SCRUM-2), see my place (FR6), edit/cancel (SCRUM-24). */
(() => {
  const REFRESH_MS = 2000;               // NFR2: refresh every 2 seconds
  const SESSION_KEY = "labline.joinCode";
  const user = LabLine.requireLogin(["STUDENT", "ADMIN"]);
  if (!user) { return; }

  const el = (id) => document.getElementById(id);
  const message = el("message");
  let session = null;        // SessionInfo from the server
  let myRequest = null;      // MyRequestStatus, or null if none
  let editing = false;
  let cancelArmed = false;
  let timer = null;

  el("user-name").textContent = user.name;
  el("logout").addEventListener("click", LabLine.logout);

  // ---------- joining ----------

  el("join-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await joinSession(el("join-code").value);
  });

  el("leave-session").addEventListener("click", () => {
    LabLine.write(SESSION_KEY, null);
    session = null;
    stopRefresh();
    render();
  });

  async function joinSession(code) {
    try {
      session = await LabLine.api("GET", "/api/sessions/join/" + encodeURIComponent(code.trim()));
      LabLine.write(SESSION_KEY, code.trim());
      LabLine.showMessage(message, "", "error");
      fillCategories();
      await refresh();
      startRefresh();
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  }

  function fillCategories() {
    const select = el("category");
    select.innerHTML = "";
    session.categories.forEach((category) => {
      const option = document.createElement("option");
      option.value = category.id;
      option.textContent = category.name;
      select.appendChild(option);
    });
  }

  // ---------- my request, refreshed every 2 s ----------

  async function refresh() {
    if (!session) { return; }
    try {
      session = await LabLine.api("GET", "/api/sessions/join/" + encodeURIComponent(LabLine.read(SESSION_KEY)));
      myRequest = await LabLine.api("GET", "/api/requests/mine?sessionId=" + session.id);
      render();
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  }

  function startRefresh() {
    stopRefresh();
    timer = setInterval(() => { if (!editing) { refresh(); } }, REFRESH_MS);
  }

  function stopRefresh() {
    if (timer) { clearInterval(timer); timer = null; }
  }

  // ---------- submit and edit ----------

  el("description").addEventListener("input", updateCounter);

  function updateCounter() {
    el("description-counter").textContent = el("description").value.length + " / 300";
  }

  el("request-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const body = {
      categoryId: Number(el("category").value),
      description: el("description").value,
      seat: el("seat").value,
    };
    try {
      if (editing) {
        await LabLine.api("PUT", "/api/requests/" + myRequest.requestId, body);
        editing = false;
        LabLine.showMessage(message, "Your request was updated.", "success");
      } else {
        body.sessionId = session.id;
        await LabLine.api("POST", "/api/requests", body);
        LabLine.showMessage(message, "You're in line.", "success");
      }
      await refresh();
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  });

  el("edit-request").addEventListener("click", () => {
    editing = true;
    el("category").value = myRequest.categoryId;
    el("description").value = myRequest.description;
    el("seat").value = myRequest.seat;
    updateCounter();
    render();
  });

  el("edit-cancel").addEventListener("click", () => {
    editing = false;
    render();
  });

  // Cancel needs a second click, so it is never done by accident.
  el("cancel-request").addEventListener("click", async () => {
    if (!cancelArmed) {
      cancelArmed = true;
      el("cancel-request").textContent = "Tap again to confirm";
      setTimeout(() => { cancelArmed = false; el("cancel-request").textContent = "Cancel request"; }, 4000);
      return;
    }
    cancelArmed = false;
    try {
      await LabLine.api("POST", "/api/requests/" + myRequest.requestId + "/cancel");
      LabLine.showMessage(message, "Your request was cancelled.", "success");
      clearForm();
      await refresh();
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  });

  function clearForm() {
    el("description").value = "";
    el("seat").value = "";
    updateCounter();
  }

  // ---------- drawing the page ----------

  function render() {
    const joined = session !== null;
    el("join-card").classList.toggle("hidden", joined);
    el("session-card").classList.toggle("hidden", !joined);
    if (!joined) {
      el("request-form-card").classList.add("hidden");
      el("status-card").classList.add("hidden");
      return;
    }

    el("session-title").textContent = session.courseCode + " · " + session.name;
    el("session-room").textContent = session.room ? "Room " + session.room : "";
    el("queue-chip").textContent = session.queueOpen ? "QUEUE OPEN" : "QUEUE CLOSED";
    el("queue-chip").className = "chip " + (session.queueOpen ? "OPEN" : "CLOSED");

    const hasRequest = myRequest !== null;
    const showForm = !hasRequest || editing;
    el("request-form-card").classList.toggle("hidden", !showForm);
    el("status-card").classList.toggle("hidden", !hasRequest || editing);
    el("request-form-title").textContent = editing ? "Edit my request" : "Ask for help";
    el("request-submit").textContent = editing ? "Save changes" : "Get in line";
    el("edit-cancel").classList.toggle("hidden", !editing);
    el("request-submit").disabled = !editing && !session.queueOpen;
    if (!editing && !session.queueOpen && !hasRequest) {
      LabLine.showMessage(message, "The queue is closed right now. You can join when a TA opens it.", "error");
    }

    if (hasRequest && !editing) { renderStatus(); }
  }

  function renderStatus() {
    el("status-chip").textContent = myRequest.status;
    el("status-chip").className = "chip " + myRequest.status;
    el("my-category").textContent = myRequest.categoryName;
    el("my-description").textContent = myRequest.description;
    el("my-seat").textContent = myRequest.seat;
    el("waiting-actions").classList.toggle("hidden", myRequest.status !== "WAITING");

    if (myRequest.status === "CLAIMED") {
      el("position").textContent = "Now";
      el("position-label").textContent = "A TA is on the way to your seat.";
    } else if (myRequest.status === "DEFERRED") {
      el("position").textContent = "–";
      el("position-label").textContent = "A TA put your request on hold. They'll return it to the queue.";
    } else {
      el("position").textContent = "#" + myRequest.position;
      el("position-label").textContent = myRequest.position === 1
          ? "You're next."
          : "in line · " + myRequest.waitingCount + " waiting";
    }
  }

  // Rejoin the last lab automatically after a page reload.
  const savedCode = LabLine.read(SESSION_KEY);
  if (savedCode) { joinSession(savedCode); } else { render(); }
})();
