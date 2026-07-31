# Branch Naming Convention

## Structure

`<type>/#<issue-number>-<description>`

Use one of the commit types from [the commit convention](convention.md). Keep the description short and use lowercase kebab-case.

## Examples

- `feat/#38-recommendation-api`
- `fix/#105-route-npe`
- `refactor/#88-event-service`
- `chore/#112-spring-ai-deps`

## Issue Extraction

Extract `<type>/#<issue-number>` from a matching branch and format the message as `<type>/#<issue-number>: <subject>`.

If the branch does not match, do not infer or invent an issue number. Ask the user for it.
