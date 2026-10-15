---
name: ci-pr-manager
description: Orchestrates the Zero-Install CI/CD and Pull Request lifecycle using GitHub Actions and GitHub CLI (gh). Covers PR creation, watching remote test and security workflows, resolving CodeQL review threads via GraphQL, and executing clean merges.
---

# Zero-Install CI & Pull Request Manager Skill

Use this skill to manage code integration and GitHub Actions validation without requiring local Maven, Java, or Docker tools.

---

## 1. Pre-PR Git Hygiene

Before creating a Pull Request:
1. Ensure the feature branch is updated from `main`:
   ```bash
   git fetch origin
   git checkout -b feat/<name>
   ```
2. Check working tree cleanliness and stage changes:
   ```bash
   git status
   git diff
   ```
3. Commit with semantic convention:
   ```bash
   git commit -m "feat(<category>): implement <name> SPI for Keycloak 26.x"
   git push -u origin feat/<name>
   ```

---

## 2. Opening the Pull Request

Create the Pull Request using GitHub CLI:
```bash
gh pr create \
  --title "feat(<category>): implement <name> SPI for Keycloak 26.x" \
  --body "### Summary of Changes..."
```

---

## 3. Monitoring Remote CI Pipelines

1. Check triggered workflow runs:
   ```bash
   gh run list --limit 3
   ```
2. Watch the CI pipeline:
   ```bash
   gh run watch <run-id>
   ```
3. Check PR status check rollup:
   ```bash
   gh pr checks <pr-number>
   ```

---

## 4. Resolving CodeQL / Bot Review Threads via GraphQL

When branch protection rulesets enforce `required_review_thread_resolution: true`:
1. Query unresolved review threads:
   ```graphql
   query {
     repository(owner: "<owner>", name: "<repo>") {
       pullRequest(number: <pr-number>) {
         reviewThreads(first: 50) {
           nodes {
             id
             isResolved
             comments(first: 1) {
               nodes {
                 id
                 path
                 body
               }
             }
           }
         }
       }
     }
   }
   ```
2. Resolve addressed or false-positive threads:
   ```graphql
   mutation {
     resolveReviewThread(input: { threadId: "<thread-id>" }) {
       thread {
         id
         isResolved
       }
     }
   }
   ```

---

## 5. Merging and Branch Cleanup

Once all checks (`CI Pipeline`, `CodeQL Security Scan`, `Dependency Review`) pass:
1. Merge the Pull Request:
   ```bash
   gh pr merge <pr-number> --merge --delete-branch
   ```
2. Switch back to `main` and pull latest changes:
   ```bash
   git checkout main
   git pull origin main
   git remote prune origin
   ```
3. Update [ROADMAP.md](file:///c:/Users/alexc/OneDrive/Documentos/projects/keycloak-custom-extensions/ROADMAP.md) marking the session completed.
