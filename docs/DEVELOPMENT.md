# Development & Contributing Guide

## Zero-Install Workflow

This project adheres to a **Zero-Install Local Policy**:
You do not need to install Java, Maven, or Docker on your local workstation.

### Workflow:
1. **Edit Code:** Make code or documentation edits locally using your preferred text editor.
2. **Branching:** Create a feature branch following the naming convention:
   * `feat/<feature-name>`
   * `fix/<fix-name>`
   * `docs/<topic>`
3. **Push & Pull Request:** Push your branch to GitHub and open a Pull Request.
4. **Automated CI Validation:** GitHub Actions automatically compiles the code with Java 21, runs linters, and executes unit and integration tests.

---

## Interactive Cloud Development (GitHub Codespaces)

If you need a complete interactive development environment with bash, Java 21, Maven, and Docker:

1. Click **Code** -> **Codespaces** -> **Create codespace on main**.
2. GitHub will launch an environment based on `.devcontainer/devcontainer.json`.
3. In the Codespace terminal, you can run:
   ```bash
   mvn clean verify
   ```
