# Architect — Setup Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 24+ | Running services |
| Docker Compose | v2+ | Orchestrating containers |
| Git | Any | Cloning the repo |

**For local development (without Docker):**
| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.9+ |
| Node.js | 20+ |
| PostgreSQL | 15+ |

---

## Step 1: Create GitHub OAuth App

1. Go to [github.com/settings/developers](https://github.com/settings/developers)
2. Click **OAuth Apps** → **New OAuth App**
3. Fill in:
   | Field | Value |
   |-------|-------|
   | Application name | `Architect` |
   | Homepage URL | `http://localhost:3000` |
   | Authorization callback URL | `http://localhost:8080/api/auth/callback` |
4. Click **Register application**
5. On the app page, copy the **Client ID**
6. Click **Generate a new client secret** and copy the secret

---

## Step 2: Configure Environment

```bash
cp .env.example .env
```

Edit `.env`:
```bash
GITHUB_CLIENT_ID=Ov23liXXXXXXXXXXXX       # from step 1
GITHUB_CLIENT_SECRET=abc123...             # from step 1
DB_PASSWORD=architect                      # can leave as-is for dev
JWT_SECRET=change-this-to-random-32chars   # change for production
```

---

## Step 3: Run with Docker Compose

```bash
# First time (builds images + starts services)
docker-compose up --build

# Subsequent runs
docker-compose up

# Run in background
docker-compose up -d
```

Services started:
| Service | Port | URL |
|---------|------|-----|
| Frontend | 3000 | http://localhost:3000 |
| Backend | 8080 | http://localhost:8080 |
| PostgreSQL | 5432 | localhost:5432/architect |

---

## Step 4: Verify Everything is Running

```bash
# Check container status
docker-compose ps

# Backend health check
curl http://localhost:8080/actuator/health

# View logs
docker-compose logs -f backend
docker-compose logs -f frontend
```

---

## Development Setup (No Docker)

### Start PostgreSQL
```bash
# Via Docker (recommended):
docker run -d \
  --name architect-pg \
  -e POSTGRES_DB=architect \
  -e POSTGRES_USER=architect \
  -e POSTGRES_PASSWORD=architect \
  -p 5432:5432 \
  postgres:16-alpine
```

### Start Backend
```bash
cd backend

# Set environment variables
export GITHUB_CLIENT_ID=your_client_id
export GITHUB_CLIENT_SECRET=your_client_secret
export DB_URL=jdbc:postgresql://localhost:5432/architect
export DB_USERNAME=architect
export DB_PASSWORD=architect
export JWT_SECRET=dev-secret-key-32-characters-long
export FRONTEND_URL=http://localhost:3000

# Run
mvn spring-boot:run
```

### Start Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## Phase 2: GitHub Webhook Setup

To receive PR events for automatic impact analysis:

1. In your GitHub repo, go to **Settings → Webhooks → Add webhook**
2. Configure:
   | Field | Value |
   |-------|-------|
   | Payload URL | `https://your-domain.com/api/webhooks/github` |
   | Content type | `application/json` |
   | Events | `Pull requests` |
3. Click **Add webhook**

For local testing, use [ngrok](https://ngrok.com):
```bash
ngrok http 8080
# Use the https:// URL as your webhook URL
```

---

## Phase 2: Slack Notifications

1. Go to [api.slack.com/apps](https://api.slack.com/apps) → Create New App
2. Enable **Incoming Webhooks** → Add to workspace → Choose channel
3. Copy the **Webhook URL**
4. Add to `.env`:
```bash
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/T00/B00/xxx
```
5. Restart the backend

---

## Production Deployment

### Environment Changes for Production

```bash
# Use a real domain
GITHUB_REDIRECT_URI=https://yourdomain.com/api/auth/callback
FRONTEND_URL=https://yourdomain.com

# Strong secret (generate with: openssl rand -hex 32)
JWT_SECRET=your-256-bit-secret-here

# Strong DB password
DB_PASSWORD=your-strong-db-password
```

### Reverse Proxy (nginx example)

```nginx
server {
    listen 443 ssl;
    server_name yourdomain.com;

    location / {
        proxy_pass http://localhost:3000;
    }

    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Backend fails to start | Check `DB_URL`, ensure PostgreSQL is running |
| OAuth redirect fails | Verify `GITHUB_REDIRECT_URI` matches exactly in GitHub OAuth App settings |
| Scan returns no endpoints | Check that the repo has the supported file types (`.java`, `.js`, `.py`, etc.) |
| JWT expired errors | Tokens expire after 24 hours — simply log in again |
| Graph shows no edges | Run a scan first; make sure at least 2 repos are connected |
| Port 8080 already in use | `lsof -i :8080` to find the process, kill it, then restart |
