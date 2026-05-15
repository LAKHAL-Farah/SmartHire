.PHONY: help install dev test clean docker-build docker-run docker-compose-up docker-compose-down lint format security-check production

# Variables
PYTHON := python3
PIP := pip3
SERVICE_NAME := face-recognition-service
VERSION := 1.0.0
IMAGE_NAME := $(SERVICE_NAME):$(VERSION)
DOCKER_REGISTRY ?= docker.io
DOCKER_IMAGE := $(DOCKER_REGISTRY)/$(IMAGE_NAME)

# Color output
BOLD := \033[1m
GREEN := \033[92m
YELLOW := \033[93m
RED := \033[91m
RESET := \033[0m

help: ## Display this help message
	@echo "$(BOLD)$(GREEN)Face Recognition Service - Makefile Commands$(RESET)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(GREEN)%-20s$(RESET) %s\n", $$1, $$2}'

# ============================================================
# Development
# ============================================================

install: ## Install dependencies
	@echo "$(YELLOW)Installing dependencies...$(RESET)"
	$(PIP) install -r requirements.txt
	@echo "$(GREEN)✓ Dependencies installed$(RESET)"

dev: install ## Run development server
	@echo "$(YELLOW)Starting development server...$(RESET)"
	$(PYTHON) face_recognition_service.py

dev-debug: install ## Run development server with debug
	@echo "$(YELLOW)Starting development server (debug mode)...$(RESET)"
	FLASK_DEBUG=true FLASK_ENV=development $(PYTHON) face_recognition_service.py

test: ## Run tests
	@echo "$(YELLOW)Running test suite...$(RESET)"
	$(PYTHON) test_client.py

test-endpoints: ## Test API endpoints with PSH
	@echo "$(YELLOW)Testing API endpoints...$(RESET)"
	pwsh -File test_endpoints.ps1

lint: ## Run linting
	@echo "$(YELLOW)Running linting...$(RESET)"
	$(PYTHON) -m pylint face_recognition_service.py --disable=all --enable=E,F || true
	@echo "$(GREEN)✓ Linting complete$(RESET)"

format: ## Format code with black
	@echo "$(YELLOW)Formatting code...$(RESET)"
	$(PYTHON) -m black face_recognition_service.py --line-length 100 || true
	@echo "$(GREEN)✓ Code formatted$(RESET)"

security-check: ## Run security checks
	@echo "$(YELLOW)Running security checks...$(RESET)"
	$(PYTHON) -m bandit face_recognition_service.py -q || true
	@echo "$(GREEN)✓ Security checks complete$(RESET)"

clean: ## Clean up generated files
	@echo "$(YELLOW)Cleaning up...$(RESET)"
	rm -rf __pycache__/ *.pyc .pytest_cache/ .mypy_cache/ dist/ build/ *.egg-info/
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	@echo "$(GREEN)✓ Cleanup complete$(RESET)"

# ============================================================
# Docker
# ============================================================

docker-build: ## Build Docker image
	@echo "$(YELLOW)Building Docker image: $(IMAGE_NAME)...$(RESET)"
	docker build -t $(IMAGE_NAME) .
	@echo "$(GREEN)✓ Image built successfully$(RESET)"

docker-run: ## Run service in Docker container
	@echo "$(YELLOW)Running Docker container...$(RESET)"
	docker run -d \
		--name $(SERVICE_NAME) \
		-p 5000:5000 \
		-e FLASK_ENV=production \
		-e FACE_RECOGNITION_MODEL=hog \
		$(IMAGE_NAME)
	@echo "$(GREEN)✓ Container started$(RESET)"
	docker logs -f $(SERVICE_NAME)

docker-stop: ## Stop Docker container
	@echo "$(YELLOW)Stopping Docker container...$(RESET)"
	docker stop $(SERVICE_NAME) || true
	docker rm $(SERVICE_NAME) || true
	@echo "$(GREEN)✓ Container stopped$(RESET)"

docker-logs: ## View Docker container logs
	docker logs -f $(SERVICE_NAME)

docker-shell: ## Open shell in running container
	docker exec -it $(SERVICE_NAME) /bin/bash

docker-compose-up: ## Start services with Docker Compose
	@echo "$(YELLOW)Starting services with Docker Compose...$(RESET)"
	docker-compose up -d
	@echo "$(GREEN)✓ Services started$(RESET)"
	@echo "$(YELLOW)Waiting for services to be ready...$(RESET)"
	sleep 5
	docker-compose logs

docker-compose-down: ## Stop services with Docker Compose
	@echo "$(YELLOW)Stopping services...$(RESET)"
	docker-compose down
	@echo "$(GREEN)✓ Services stopped$(RESET)"

docker-compose-logs: ## View Docker Compose logs
	docker-compose logs -f face-recognition-service

docker-compose-ps: ## List Docker Compose containers
	docker-compose ps

docker-push: docker-build ## Build and push to registry
	@echo "$(YELLOW)Pushing image to registry: $(DOCKER_IMAGE)...$(RESET)"
	docker tag $(IMAGE_NAME) $(DOCKER_IMAGE)
	docker push $(DOCKER_IMAGE)
	@echo "$(GREEN)✓ Image pushed$(RESET)"

# ============================================================
# Production
# ============================================================

production: install ## Run in production mode with gunicorn
	@echo "$(YELLOW)Starting production server...$(RESET)"
	gunicorn -c gunicorn_config.py face_recognition_service:app

# ============================================================
# Utilities
# ============================================================

check-python: ## Check Python version
	@echo "Checking Python version..."
	@$(PYTHON) --version

check-deps: ## Check if required packages are installed
	@echo "Checking dependencies..."
	@$(PYTHON) -c "import flask, face_recognition, PIL, numpy" && echo "$(GREEN)✓ All dependencies installed$(RESET)" || echo "$(RED)✗ Missing dependencies$(RESET)"

requirements-freeze: ## Freeze current dependencies
	@echo "$(YELLOW)Freezing dependencies...$(RESET)"
	$(PIP) freeze > requirements-frozen.txt
	@echo "$(GREEN)✓ Dependencies frozen to requirements-frozen.txt$(RESET)"

health-check: ## Check service health
	@echo "$(YELLOW)Checking service health...$(RESET)"
	@curl -s http://localhost:5000/health | $(PYTHON) -m json.tool || echo "$(RED)✗ Service is not responding$(RESET)"

# ============================================================
# Setup
# ============================================================

setup-env: ## Create .env from template
	@if [ ! -f .env ]; then \
		echo "$(YELLOW)Creating .env from .env.example...$(RESET)"; \
		cp .env.example .env; \
		echo "$(GREEN)✓ .env created$(RESET)"; \
	else \
		echo "$(YELLOW).env already exists$(RESET)"; \
	fi

setup-venv: ## Create Python virtual environment
	@echo "$(YELLOW)Creating virtual environment...$(RESET)"
	$(PYTHON) -m venv venv
	@echo "$(GREEN)✓ Virtual environment created$(RESET)"
	@echo "$(YELLOW)Activate with:$(RESET)"
	@echo "  On Windows: venv\\Scripts\\activate"
	@echo "  On Linux/Mac: source venv/bin/activate"

setup: setup-venv setup-env install ## Complete setup
	@echo "$(GREEN)✓ Setup complete$(RESET)"

# ============================================================
# Full Workflows
# ============================================================

all: clean setup test ## Complete workflow: clean, setup, test
	@echo "$(GREEN)✓ All tasks completed$(RESET)"

dev-all: clean setup dev ## Development workflow

docs: ## Generate documentation
	@echo "$(YELLOW)Documentation is in README.md$(RESET)"
	@cat README.md | head -50

version: ## Show version
	@echo "$(GREEN)Face Recognition Service$(RESET)"
	@echo "Version: $(VERSION)"

.DEFAULT_GOAL := help
