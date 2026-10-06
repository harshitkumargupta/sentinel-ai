# ADR-003: Zero-cost deployment with Docker Compose + Caddy + Cloudflare Tunnel

- **Status**: Accepted
- **Date**: 2026-10-06
- **Context**: Phase 15 (CI/CD and deployment)

## Context

SentinelAI is a final-year capstone that must be demonstrably deployable and publicly reachable over
HTTPS, with CI/CD, **without incurring any cost** and without AWS (explicitly out of scope until a
later phase). It is a single-tenant, single-node application; horizontal scale and managed HA are
not current requirements.

## Decision

Deploy as Docker Compose on a single host, front everything with **Caddy** (automatic Let's Encrypt
HTTPS), publish images to **GitHub Container Registry**, build/test/scan/publish via **GitHub
Actions**, and expose the app publicly — when wanted — through a **Cloudflare Tunnel** (free, no open
ports). Deploys run through `scripts/deploy.sh` with health checks, a smoke test, and automatic
rollback to the previous image tag.

## Alternatives considered

- **AWS (ECS/Fargate, RDS, ALB, Route53, ACM)**: the production-grade target, but it costs money,
  pulls in account/IAM/networking setup, and is explicitly deferred. Rejected for now.
- **Kubernetes (k3s/EKS)**: operationally heavy for a single-node app; EKS costs money. Deferred to
  the later K8s phase.
- **PaaS free tiers (Heroku/Render/Fly)**: free tiers are shrinking, sleep apps, or require a card;
  less control and not reliably $0.

## Consequences

- **Positive**: truly $0; identical workflow locally and on a free VM; fast to stand up; HTTPS and a
  public URL without touching firewall/DNS; rollback and backup are one script each.
- **Negative**: single node (no HA/autoscaling); manual VM patching; Compose lacks k8s-style rolling
  orchestration (mitigated by health-gated deploy + auto-rollback).

## What changes to move to AWS later

- Images already live in a registry → push the same images to ECR (or keep GHCR).
- Replace Compose services with ECS/Fargate task definitions (or an EKS Helm chart); the Dockerfiles
  are unchanged.
- MySQL container → RDS; Redis container → ElastiCache (both already optional/configurable via env).
- Caddy/Tunnel → ALB + ACM + Route53 for TLS and DNS.
- `deploy.sh` logic (pull, health-gate, smoke, rollback) maps onto a CodeDeploy/ECS deployment with
  circuit-breaker rollback. CI already builds and scans images, so only the deploy target changes.
