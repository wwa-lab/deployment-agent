# Docker Guide

This document provides example Docker image names and the Docker commands to build, push, pull, and run the backend and frontend services for this repository.

## Image Names

- Backend image: `ghcr.io/wwa-lab/deployment-agent-backend:latest`
- Frontend image: `ghcr.io/wwa-lab/deployment-agent-frontend:latest`

If you want to publish a versioned release, replace `latest` with a concrete tag such as `v0.1.0`.

## Backend

### Build Backend Image

```bash
docker build -t ghcr.io/wwa-lab/deployment-agent-backend:latest .
```

### Push Backend Image

Login to GitHub Container Registry first:

```bash
echo "$GITHUB_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

Push the backend image:

```bash
docker push ghcr.io/wwa-lab/deployment-agent-backend:latest
```

### Pull Backend Image

```bash
docker pull ghcr.io/wwa-lab/deployment-agent-backend:latest
```

### Run Backend Container

Run with the local profile and in-memory H2:

```bash
docker network create deployment-agent-net
docker run -d \
  --name backend \
  --network deployment-agent-net \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  ghcr.io/wwa-lab/deployment-agent-backend:latest
```

Run with Oracle settings:

```bash
docker run -d \
  --name backend \
  --network deployment-agent-net \
  -p 8080:8080 \
  -e DB_URL='jdbc:oracle:thin:@host.docker.internal:1521/XEPDB1' \
  -e DB_USERNAME='da_user' \
  -e DB_PASSWORD='changeme' \
  -e SPRING_PROFILES_ACTIVE=test \
  -e APP_CONFIG_CRYPTO_SECRET='change-me-in-real-env' \
  ghcr.io/wwa-lab/deployment-agent-backend:latest
```

## Frontend

### Build Frontend Image

```bash
docker build -t ghcr.io/wwa-lab/deployment-agent-frontend:latest ./frontend
```

### Push Frontend Image

```bash
docker push ghcr.io/wwa-lab/deployment-agent-frontend:latest
```

### Pull Frontend Image

```bash
docker pull ghcr.io/wwa-lab/deployment-agent-frontend:latest
```

### Run Frontend Container

The frontend `nginx` config proxies `/api` requests to `http://backend:8080`, so the backend container must be reachable on the same Docker network with the container name `backend`.

```bash
docker run -d \
  --name frontend \
  --network deployment-agent-net \
  -p 80:80 \
  ghcr.io/wwa-lab/deployment-agent-frontend:latest
```

## Pull And Run Both Services

```bash
docker network create deployment-agent-net

docker pull ghcr.io/wwa-lab/deployment-agent-backend:latest
docker pull ghcr.io/wwa-lab/deployment-agent-frontend:latest

docker run -d \
  --name backend \
  --network deployment-agent-net \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  ghcr.io/wwa-lab/deployment-agent-backend:latest

docker run -d \
  --name frontend \
  --network deployment-agent-net \
  -p 80:80 \
  ghcr.io/wwa-lab/deployment-agent-frontend:latest
```

After startup:

- Frontend: `http://localhost`
- Backend API: `http://localhost:8080/api/deployment-agent`

## Useful Docker Commands

Show running containers:

```bash
docker ps
```

View backend logs:

```bash
docker logs -f backend
```

View frontend logs:

```bash
docker logs -f frontend
```

Stop and remove containers:

```bash
docker rm -f frontend backend
```
