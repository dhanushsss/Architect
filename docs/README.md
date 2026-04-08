# Zerqis docs

## What we are doing

Zerqis gives engineering teams a reliable way to understand **cross-repo API impact before merge**.

## What is the problem

Service dependencies are usually implicit. Teams discover breakage late (during QA/production) because they cannot quickly answer: "who depends on this API change?"

## How we solve it

- Scan connected GitHub repos for endpoints, calls, and imports.
- Build and maintain a dependency graph in PostgreSQL.
- Run targeted PR analysis from webhook events.
- Post explainable PR comments (deterministic verdict + confidence breakdown + optional AI explanation).
- Log prediction data for ongoing trust calibration.

## End-to-end flow

1. Connect repo and run deep scan.
2. Webhook receives PR opened/synchronize event.
3. PR analysis extracts changed endpoints and maps downstream impact.
4. Zerqis posts PR comment and optional status/slack.
5. Prediction is saved for later quality tuning.

Full product overview: [../README.md](../README.md).

| Doc | Contents |
|-----|----------|
| [SETUP.md](SETUP.md) | Environment variables & OAuth |
| [API.md](API.md) | REST API overview |
| [PR_ENGINE.md](PR_ENGINE.md) | GitHub PR webhook & flags |
