/* FAQ board page (SCRUM-15): everyone reads the FAQs, TAs can add one. */
(() => {
  const SESSION_KEY = "labline.joinCode";
  const user = LabLine.requireLogin(["STUDENT", "TA", "ADMIN"]);
  if (!user) { return; }

  const el = (id) => document.getElementById(id);
  const message = el("message");
  let sessionId = null;

  el("user-name").textContent = user.name;
  el("logout").addEventListener("click", LabLine.logout);
  el("back").addEventListener("click", () => window.history.back());

  // Only TAs see the "add" form.
  const isTa = LabLine.currentUser().role === "TA";
  el("faq-add-card").classList.toggle("hidden", !isTa);

  // The session comes from ?session=ID, or from the join code saved by the student page.
  async function findSessionId() {
    const fromUrl = new URLSearchParams(window.location.search).get("session");
    if (fromUrl) { return Number(fromUrl); }
    const code = LabLine.read(SESSION_KEY);
    if (!code) { return null; }
    const session = await LabLine.api("GET", "/api/sessions/join/" + encodeURIComponent(code));
    return session.id;
  }

  async function loadFaqs() {
    try {
      const faqs = await LabLine.api("GET", "/api/sessions/" + sessionId + "/faq");
      renderFaqs(faqs);
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  }

  function renderFaqs(faqs) {
    const list = el("faq-list");
    list.innerHTML = "";
    el("faq-empty").classList.toggle("hidden", faqs.length > 0);
    faqs.forEach((faq) => {
      const card = document.createElement("div");
      card.className = "card";
      const question = document.createElement("h3");
      question.style.margin = "0 0 8px";
      question.textContent = faq.question;     // textContent, never innerHTML
      const answer = document.createElement("p");
      answer.style.margin = "0";
      answer.textContent = faq.answer;
      card.appendChild(question);
      card.appendChild(answer);
      list.appendChild(card);
    });
  }

  el("faq-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const body = {
      question: el("faq-question").value,
      answer: el("faq-answer").value,
    };
    try {
      await LabLine.api("POST", "/api/ta/sessions/" + sessionId + "/faq", body);
      el("faq-question").value = "";
      el("faq-answer").value = "";
      LabLine.showMessage(message, "FAQ added.", "success");
      await loadFaqs();
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  });

  (async () => {
    try {
      sessionId = await findSessionId();
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
      return;
    }
    if (!sessionId) {
      LabLine.showMessage(message, "Join a lab first, then open the FAQ board.", "error");
      return;
    }
    await loadFaqs();
  })();
})();