# Multi-stage build for Face Recognition Service

# ========================================
# Stage 1: Builder (with CMake for dlib)
# ========================================
FROM python:3.10-slim as builder

WORKDIR /build

# Install build dependencies needed for face_recognition library
# (dlib requires CMake and build tools)
RUN apt-get update && apt-get install -y \
    cmake \
    gcc \
    g++ \
    make \
    git \
    build-essential \
    libopenblas-dev \
    liblapack-dev \
    libx11-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY requirements.txt .

# Build wheels for all dependencies
RUN pip install --no-cache-dir --upgrade pip setuptools wheel && \
    pip wheel --no-cache-dir --wheel-dir /build/wheels -r requirements.txt

# ========================================
# Stage 2: Runtime (lightweight)
# ========================================
FROM python:3.10-slim

WORKDIR /app

# Install runtime dependencies only
RUN apt-get update && apt-get install -y \
    libopenblas-dev \
    liblapack-dev \
    libx11-6 \
    libjpeg62-turbo \
    zlib1g \
    && rm -rf /var/lib/apt/lists/*

# Copy wheels from builder
COPY --from=builder /build/wheels /wheels

# Install Python dependencies from wheels
COPY requirements.txt .
RUN pip install --no-cache-dir \
    --no-index --find-links=/wheels \
    -r requirements.txt && \
    rm -rf /wheels

# Copy application code
COPY face_recognition_service.py .
COPY docker-entrypoint.sh .

# Create non-root user for security
RUN useradd -m -u 1000 appuser && \
    chown -R appuser:appuser /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s CMD wget -qO- http://localhost:5000/health || exit 1

# Expose port
EXPOSE 5000

# Set entrypoint
ENTRYPOINT ["/bin/bash", "docker-entrypoint.sh"]

# Default command - run with gunicorn for production
CMD ["gunicorn", "--workers", "4", "--worker-class", "sync", "--bind", "0.0.0.0:5000", "--timeout", "120", "--access-logfile", "-", "--error-logfile", "-", "face_recognition_service:app"]
