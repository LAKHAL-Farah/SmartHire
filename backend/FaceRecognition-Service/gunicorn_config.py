"""
Gunicorn Configuration for Face Recognition Service
Configuration pour déploiement en production

Usage:
    gunicorn -c gunicorn_config.py face_recognition_service:app
"""

import os
import multiprocessing

# ============================================================
# Server Socket Configuration
# ============================================================

# Bind address(es)
bind = f"{os.getenv('HOST', '0.0.0.0')}:{os.getenv('PORT', '5000')}"

# Whether to set the SO_REUSEADDR flag on the listening socket
reuse_port = True

# ============================================================
# Worker Configuration
# ============================================================

# Number of worker processes
# Rule of thumb: (2 x CPU_CORES) + 1
# For containers, usually 2-4 workers
workers = int(os.getenv('GUNICORN_WORKERS', multiprocessing.cpu_count() * 2 + 1))

# Worker class
# 'sync' (default) - synchronized worker
# 'eventlet' - greenlet-based worker (requires eventlet)
# 'gevent' - greenlet-based worker (requires gevent)
worker_class = 'sync'

# Maximum number of simultaneous clients
# Limit the number of connections a worker can have
max_requests = 1000
max_requests_jitter = 50

# Worker timeout (seconds)
# If a worker is processing a request for longer than this, it will be killed
timeout = int(os.getenv('GUNICORN_TIMEOUT', 120))

# Graceful timeout (seconds)
graceful_timeout = 30

# Keep alive timeout
keepalive = 5

# ============================================================
# Logging Configuration
# ============================================================

# Access log file
accesslog = os.getenv('GUNICORN_ACCESS_LOG', '-')

# Error log file
errorlog = os.getenv('GUNICORN_ERROR_LOG', '-')

# LogLevel
loglevel = os.getenv('GUNICORN_LOG_LEVEL', 'info')

# Access log format
access_log_format = '%(h)s %(l)s %(u)s %(t)s "%(r)s" %(s)s %(b)s "%(f)s" "%(a)s" %(D)s'

# ============================================================
# Process Naming
# ============================================================

proc_name = 'face-recognition-service'

# ============================================================
# Server Hooks
# ============================================================

def on_starting(server):
    """Called just before the master process is initialized."""
    print("="*60)
    print("Face Recognition Service - Gunicorn Server Starting")
    print("="*60)
    print(f"Workers: {server.cfg.workers}")
    print(f"Worker Class: {server.cfg.worker_class}")
    print(f"Bind: {server.cfg.bind}")
    print(f"Timeout: {server.cfg.timeout}s")
    print("="*60)


def when_ready(server):
    """Called just after the server is started."""
    print("")
    print("✓ Gunicorn server is ready. Spawning workers")
    print("")


def on_exit(server):
    """Called just before exiting Gunicorn."""
    print("")
    print("="*60)
    print("Face Recognition Service - Gunicorn Server Shutting Down")
    print("="*60)
    print("")


# ============================================================
# Application Configuration
# ============================================================

# Forward allow header
forwarded_allow_ips = '*'

# Secure header (X-Forwarded-Proto)
secure_scheme_headers = {
    'X_FORWARDED_PROTOCOL': 'ssl',
    'X_FORWARDED_PROTO': 'https',
    'X_FORWARDED_SSL': 'on',
}

# ============================================================
# SSL Configuration (optional, disable by default)
# ============================================================

# Uncomment to enable SSL
# keyfile = '/path/to/keyfile'
# certfile = '/path/to/certfile'
# ssl_version = 'TLSv1_2'
# cert_reqs = 2
# ca_certs = '/path/to/ca_certs'
# ciphers = 'TLSv1_2:!aNULL:!eNULL'

# ============================================================
# Environment
# ============================================================

# Enable Python optimizations
# -O flag equivalent
# preload_app = True

# Run as daemon (not recommended with Docker)
# daemon = False

# ============================================================
# Thread Configuration (for threaded workers)
# ============================================================

# Number of threads (for threaded worker)
# threads = 2

# ============================================================
# Performance Tuning
# ============================================================

# TCP backlog
backlog = 2048

# Deferred accept on Linux
defer_accept = True

# TCP_NODELAY
tcp_nodelay = True

# ============================================================
# Development Configuration
# ============================================================

# Enable automatic reload on code changes (development only!)
reload = os.getenv('GUNICORN_RELOAD', 'false').lower() == 'true'

# Reload extra files
# reload_extra_files = ['/path/to/config.py']

# Enable debugging
# debug = True

# ============================================================
# Custom Settings
# ============================================================

# Add any custom configuration here
# raw_env = ['DB_CONNECTION=postgresql://localhost/db']
