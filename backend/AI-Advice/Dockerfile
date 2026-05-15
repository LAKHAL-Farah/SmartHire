FROM python:3.11-slim AS build
WORKDIR /app
COPY requirements.txt ./
RUN apt-get update && apt-get install -y build-essential gcc libpq-dev --no-install-recommends && rm -rf /var/lib/apt/lists/*
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install --upgrade pip
RUN pip install -r requirements.txt
COPY . .

FROM python:3.11-slim
RUN groupadd -r appuser && useradd -r -g appuser appuser
COPY --from=build /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
WORKDIR /app
COPY --from=build /app /app
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s CMD wget -qO- http://localhost:8090/health || exit 1
CMD ["gunicorn","-b","0.0.0.0:8090","app:app"]
