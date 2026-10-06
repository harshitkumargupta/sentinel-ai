# syntax=docker/dockerfile:1
FROM python:3.12-slim AS runtime

# Unprivileged user.
RUN groupadd --system sentinel && useradd --system --gid sentinel --home /app sentinel
WORKDIR /app

# Install deps first for layer caching; curl for the healthcheck only.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# App code and any pre-trained model artifacts.
COPY . .
RUN chown -R sentinel:sentinel /app
USER sentinel

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8000/health || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
