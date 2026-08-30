## Security rules

- Never read, print, copy, modify, or summarize `.env*` files.
- Never inspect files matching:
  - `application-prod.properties`
  - `application-secret.properties`
  - `*credentials*`
  - `*secret*`
  - `*.pem`
  - `*.key`
- Never run `env`, `printenv`, `set`, or commands intended to enumerate environment variables.
- Never include credentials, tokens, cookies, private keys, or connection strings in logs or responses.
- Use `.env.example` and placeholder values when configuration examples are needed.
- Ask for confirmation before accessing production configuration.