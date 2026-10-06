# syntax=docker/dockerfile:1
# ---- Build stage: produce the static Vite bundle ----
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Runtime stage: serve the bundle with a hardened, non-root Nginx ----
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
# nginx-unprivileged already runs as uid 101 (non-root) and listens on 8080.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://localhost:8080/ || exit 1
