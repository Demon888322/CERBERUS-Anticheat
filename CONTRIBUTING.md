# Contributing to CERBERUS

Contributions that improve correctness, reliability, compatibility, or
documentation are welcome.

## Before making a change

- For a major behavior change or new check, open an issue or discussion first
  so the scope and false-positive risks can be agreed upon.
- Do not submit credentials, player data, server logs containing private
  information, or copyrighted client code.
- Keep changes focused and document configuration changes in the default
  `config.yml` and README.

## Development checks

Requirements are listed in the README. Before opening a pull request, run:

```shell
mvn --batch-mode clean verify
```

For checks that need a Paper server, test on an isolated development server
with PacketEvents enabled. Include the server version and a concise test
summary in the pull request. Do not test automatic punishments on a production
server.

## Pull requests

Include:

- What changed and why.
- Any behavior or configuration changes, including migration considerations.
- Build/test results and manual server test details where applicable.
- Documentation updates for user-visible commands or settings.

Prefer small, reviewable pull requests. Avoid unrelated formatting or generated
files.

## Bug reports

Provide CERBERUS version, Paper build, Java version, relevant configuration
with secrets removed, a redacted log excerpt, and reliable reproduction steps.
Do not publish player IP addresses, credentials, or private server data.

For security vulnerabilities, do not publish exploit steps in a public issue.
Send a minimal report through a private channel published by the project
maintainers.
