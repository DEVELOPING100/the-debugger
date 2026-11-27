/* Log in / sign up page (SCRUM-1). */
(() => {
  const message = document.getElementById("message");
  const loginForm = document.getElementById("login-form");
  const signupForm = document.getElementById("signup-form");
  const loginTab = document.getElementById("tab-login");
  const signupTab = document.getElementById("tab-signup");

  const existing = LabLine.currentUser();
  if (existing) { window.location.href = LabLine.homePageFor(existing.role); }

  function showTab(isLogin) {
    loginForm.classList.toggle("hidden", !isLogin);
    signupForm.classList.toggle("hidden", isLogin);
    loginTab.classList.toggle("active", isLogin);
    signupTab.classList.toggle("active", !isLogin);
    LabLine.showMessage(message, "", "error");
  }

  loginTab.addEventListener("click", () => showTab(true));
  signupTab.addEventListener("click", () => showTab(false));

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const user = await LabLine.login(
          document.getElementById("login-email").value,
          document.getElementById("login-password").value);
      window.location.href = LabLine.homePageFor(user.role);
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  });

  signupForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = document.getElementById("signup-email").value;
    const password = document.getElementById("signup-password").value;
    try {
      await LabLine.register(email, document.getElementById("signup-name").value, password);
      const user = await LabLine.login(email, password);
      window.location.href = LabLine.homePageFor(user.role);
    } catch (error) {
      LabLine.showMessage(message, error.message, "error");
    }
  });
})();
