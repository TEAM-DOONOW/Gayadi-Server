---
name: git-commit-assistant
description: Analyze Git changes and prepare commits according to the Gayadi-Server conventions. Use when the user asks to review changes, draft a commit message, stage files, create a commit, finish a feature or bug fix, or prepare changes for a pull request.
---

# Git Commit Assistant

Follow the project format: `<type>/#<issue-number>: <subject>`.

## Workflow

1. Read [the commit convention](references/convention.md) and [the branch convention](references/branch_convention.md).
2. Inspect with `git branch --show-current`, `git status --short`, `git diff`, and `git diff --cached`.
3. Separate staged and unstaged changes. Identify unrelated changes and potential secrets such as credentials, tokens, signing files, or `.env` files.
4. Do not stage unrelated files or potential secrets. Do not use `git add .` or `git add -A` by default; stage only explicitly selected paths.
5. Select the commit type from the actual diff. Prefer a valid branch type when it agrees with the diff; otherwise explain the discrepancy.
6. Extract the type and issue number from `<type>/#<issue-number>-<description>`. If none exists, do not invent one; ask the user because this project requires it.
7. Draft a Korean subject by default, or English when requested. Keep it within 50 characters and follow the language-specific rules.
8. Propose the message and, when helpful, `git commit -m "<type>/#<issue-number>: <subject>"`.
9. Treat requests to review, draft, or prepare as proposal-only. Execute `git commit` only when the user explicitly asks to create the commit.
10. Before committing, show the files and review the staged diff. After committing, report the hash, message, and committed files.

## Examples

```text
feat/#10: 여행 일정 생성 API 구현
fix/#18: 경로 추천 NPE 수정
refactor/#27: 이벤트 서비스 구조 개선
docs/#35: README API 목록 수정
chore/#41: Spring AI 의존성 버전 업데이트
```

## References

- [Commit convention](references/convention.md): message format, types, and subject rules.
- [Branch convention](references/branch_convention.md): branch format and issue-number extraction.
