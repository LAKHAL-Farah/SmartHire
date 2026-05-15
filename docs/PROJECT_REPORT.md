# SmartHire — Project & Backend Services Report

This document summarizes the SmartHire application, with a service-by-service description of backend components, responsibilities, technology, configuration notes, and deployment considerations. It is intended for recruiters, technical reviewers, and engineers onboarding the project.

## High-level overview

- Type: Distributed microservices application for AI-assisted interviewing and candidate optimization.
- Frontend: Angular SPA (in `/frontend`).
- Backend: Java Spring Boot microservices (in `/backend/*`), Python AI workers for model tasks (in `backend/AI-Advice` and `FaceRecognition-Service`).
- Orchestration: Docker Compose for local dev, Kubernetes manifests in `/k8s` for production.
- Data: MySQL for relational data; optional vector DB for RAG (not yet included).

## Backend services

Below are the main backend services and a concise description of what each does.

- `ConfigServer/`
  - Purpose: Centralized Spring Cloud Config server hosting configuration for services.
  - Tech: Spring Boot, Git-backed config repository under `config-repo/`.
  - Notes: Keep sensitive values out of repo; use environment or K8s Secrets.

- `Discovery/` (Eureka)
  - Purpose: Service registry for runtime discovery between microservices.
  - Tech: Spring Cloud Netflix Eureka server.
  - Notes: Required for local service-to-service routing when not using DNS-based service discovery.

- `Gateway/`
  - Purpose: API gateway and single entry point for frontend and external clients; handles routing, authentication checks, and request authorization rules.
  - Tech: Spring Cloud Gateway (Spring Boot).
  - Typical port: 8887 (configurable); ensures route-based role permissions.

- `MS-User/`
  - Purpose: User and identity management (accounts, roles such as candidate/recruiter/admin), authentication token issuance, user profile endpoints.
  - Tech: Spring Boot, likely JWT-based auth.
  - Notes: Integrates with gateway for auth, stores users in MySQL.

- `MS-Assessment/`
  - Purpose: Stores and runs assessments (questions, scoring, results). Exposes admin endpoints for assessment management and publishes results to `MS-User` when appropriate.
  - Tech: Spring Boot; has profile-specific properties (dev, docker). Connects to MySQL and optional AI microservices for question generation.

- `MS-Interview/`
  - Purpose: Orchestrates live interview sessions (question flow, timing, real-time status) and coordinates with AI orchestrator for prompts and scoring.
  - Tech: Spring Boot; uses WebSockets for live interview state and communicates with AI workers.

- `MS_JOB/`
  - Purpose: Jobs module — posting jobs, resume storage, and integration points to score/augment job descriptions via AI.
  - Tech: Spring Boot; stores resumes on local storage or object storage; integrates with NVIDIA AI adapters for profile transformation.

- `MS-Profile/`
  - Purpose: Candidate profile enrichment, GitHub/LinkedIn/CV scoring and optimization features.
  - Tech: Spring Boot; calls Python AI workers for model-based scoring.

- `MS-Roadmap/`
  - Purpose: Learning roadmap, recommending upskilling content (Udemy/Coursera/YouTube integrations).
  - Tech: Spring Boot; integrates external provider APIs via keys provided at runtime.

- `MS-EventMangement/`
  - Purpose: Event-driven components, asynchronous workflows, or internal event management (naming suggests event handling).
  - Tech: Spring Boot; used for domain events and integrations.

- `FaceRecognition-Service/` (Python)
  - Purpose: Optional multimodal service for face detection/recognition used in profile verification or interview proctoring.
  - Tech: Python (face_recognition or similar libraries), containerized with its own requirements.
  - Notes: Ensure privacy and legal compliance if enabled.

- `AI-Advice/` (Python)
  - Purpose: Lightweight Python AI worker for CV/advice generation, prompt orchestration, or model-driven features (job-matching text transforms).
  - Tech: Python, scikit/ML artifacts (models under `AI-Advice/`), exposes HTTP endpoints for synchronous calls.

## Cross-cutting concerns

- Data storage: MySQL is the primary relational store. Each service has its own schema/DB name defined in properties and should use environment variables for passwords.
- Secrets & configuration: Use `ConfigServer` for non-secret config and a secrets manager / Kubernetes Secrets for credentials. Example `.env.example` files and `application.properties.example` files have been added to the repo.
- Real-time comms: Interviews use WebSockets; ensure gateway permits WS upgrades and sticky sessions where needed.
- AI integration: Model adapters are pluggable — the codebase contains placeholders and environment-driven keys for providers (OpenAI, NVIDIA, Groq, ElevenLabs). Keys must be supplied at runtime.
- Secure execution: Candidate code is evaluated in Docker sandboxes to prevent host compromise. Keep sandbox images minimal and enforce CPU/memory limits.

## Developer & deployment notes

- Local dev: `docker compose up -d` starts the full stack; copy `.env.example` to `.env` and fill values locally.
- Kubernetes: Manifests are in `/k8s`; production secrets should be created via `kubectl create secret generic` or your cloud secrets manager.
- Recommended CI/CD: Build each Java service with `./mvnw -DskipTests package`, build Docker images, and deploy images via registry with immutable tags.

## Security & compliance

- Remove secrets from git history if they were ever committed (BFG / git filter-repo).
- Rotate any API keys that may have been exposed.
- Face recognition and voice capture features require clear privacy notices and opt-in consent.

## Suggested roadmap / improvements

- Add a Vector DB service (e.g., Milvus / Pinecone / Weaviate) for RAG and interview memory.
- Harden sandboxing for code evaluation (gVisor / Firecracker or Kubernetes-based sandboxing).
- Add automated secret scanning in CI (detect-secrets, truffleHog).
- Add per-service README files consolidated under `/docs/services/` (for recruiter-friendly descriptions).

## Contact / ownership

For demo screenshots, run the local stack and capture the UI pages into `/docs/screenshots/` using the recommended filenames. For further refinement of the project report (e.g., adding sequence diagrams, port maps, or detailed API docs), I can generate those on request.
