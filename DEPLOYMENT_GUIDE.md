# Deployment Guide: Book-My-Event

## 📋 Prerequisites

- Git
- Docker & Docker Compose
- Java 21 (for local development only)
- Maven 3.8+ (or use the provided wrapper)

## 🚀 Quick Local Setup (2 minutes)

```bash
# 1. Clone the repository
git clone <your-repo-url> Book-My-Event
cd Book-My-Event

# 2. Start PostgreSQL
docker-compose up db -d

# 3. Build and run (if Maven is available)
./mvnw clean spring-boot:run

# 4. Verify it works
curl http://localhost:8080/health/live

# 5. Run burst test
./burst.sh http://localhost:8080
```

## 🐳 Docker Compose (Full Stack)

Start the entire application stack (app + database):

```bash
docker-compose up --build
```

**What it does:**
- Builds the Docker image from `Dockerfile`
- Starts PostgreSQL database
- Starts the Spring Boot application
- Runs Flyway migrations automatically
- Exposes app on port 8080

**Verify:**
```bash
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
curl http://localhost:8080/actuator/metrics/prometheus
```

**Logs:**
```bash
docker-compose logs -f app
docker-compose logs -f db
```

**Stop everything:**
```bash
docker-compose down
docker-compose down -v  # Also remove volumes
```

## ☁️ Deploy to Render (Free Tier)

### Step 1: Push to GitHub

```bash
git add .
git commit -m "Book-My-Event: Seat reservation service"
git push origin main
```

### Step 2: Create PostgreSQL Database on Render

1. Go to https://render.com
2. Sign in with GitHub
3. Click "New +" → "PostgreSQL"
4. **Settings:**
   - Name: `book-my-event-db`
   - Database: `book_my_event`
   - User: `postgres`
   - Keep auto-generated password
   - Region: Choose closest to you
   - Plan: Free tier
5. Create database
6. Copy the connection string from the dashboard

### Step 3: Create Web Service

1. Back on Render dashboard
2. Click "New +" → "Web Service"
3. Connect your GitHub repository
4. **Settings:**
   - Name: `book-my-event`
   - Environment: `Docker`
   - Region: Same as database
   - Branch: `main`
   - Plan: Free tier (auto-sleep after 15 min of inactivity)

5. **Environment Variables** (set these):
   ```
   SPRING_DATASOURCE_URL=postgresql://<host>:<port>/book_my_event
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=<your-password-from-step-2>
   SPRING_JPA_HIBERNATE_DDL_AUTO=validate
   SPRING_FLYWAY_ENABLED=true
   ```
   Find connection details in PostgreSQL dashboard under "Connections"

6. Advanced settings:
   - Health check path: `/health/ready`
   - Auto-deploy: ON

7. Click "Create Web Service"

**Wait for deployment (~3-5 minutes)**

### Step 4: Verify Deployment

```bash
# Find your service URL in the Render dashboard
# It should look like: https://book-my-event-xxxxx.onrender.com

# Test it
curl https://book-my-event-xxxxx.onrender.com/health/live

# Run burst test against live service
./burst.sh https://book-my-event-xxxxx.onrender.com

# View metrics
curl https://book-my-event-xxxxx.onrender.com/actuator/metrics/prometheus

# View logs
# Logs are available in the Render dashboard under "Logs"
```

## 🚀 Deploy to Railway

### Step 1: Create Railway Project

1. Go to https://railway.app
2. Create a new project → Import from GitHub
3. Select your `Book-My-Event` repository

### Step 2: Add Database

1. In the Railway dashboard, click "Add"
2. Select "PostgreSQL"
3. Railway will automatically wire it to your app

### Step 3: Configure Environment

Railway auto-detects Spring Boot and sets up environment variables. If needed, override:
```
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

### Step 4: Deploy

Click "Deploy" → Railway builds and deploys automatically

Check logs:
```bash
railway logs -f
```

## 🚀 Deploy to Fly.io

### Step 1: Install Flyctl

```bash
curl -L https://fly.io/install.sh | sh
fly auth login
```

### Step 2: Initialize Fly App

```bash
fly launch --image book-my-event
# Answer prompts:
# - Add PostgreSQL? YES
# - Choose region
```

### Step 3: Deploy

```bash
fly deploy
```

### Step 4: Access Your App

```bash
fly open  # Opens browser to your app
fly logs  # View logs
```

## 🧪 Load Testing Against Deployed Service

```bash
# Replace URL with your deployed service
./burst.sh https://book-my-event-xxxxx.onrender.com

# Expected output (with 500 concurrent users):
# - 1 success (201)
# - 499 conflicts (409)
# - 0 server errors (5xx)
# - Reconciliation: available + held + confirmed = total
```

## 📊 Monitor Metrics

```bash
# Prometheus endpoint (if exposed)
curl https://your-deployed-url/actuator/metrics/prometheus

# Specific metrics
curl https://your-deployed-url/actuator/metrics/reservations.confirmed
curl https://your-deployed-url/actuator/metrics/reservations.declined.seat_taken
```

## 🔍 Troubleshooting

### App not starting

**Check logs:**
```bash
docker-compose logs app
```

**Common issues:**
- Database connection string incorrect → Fix in environment
- Database not ready → Wait 10s before starting app
- Port 8080 already in use → Change in docker-compose.yml

### Database errors

```bash
# Verify DB is running
docker-compose ps

# Check DB logs
docker-compose logs db

# Connect directly to DB
psql postgresql://postgres:postgres@localhost:5432/book_my_event
\dt  # List tables
```

### Metrics not showing

```bash
curl http://localhost:8080/actuator/metrics  # List all metrics
curl http://localhost:8080/actuator/metrics/reservations.confirmed  # Specific metric
```

### Burst test failing

1. Verify service is running: `curl http://localhost:8080/health/ready`
2. Check database has started: `docker-compose logs db`
3. Verify jq is installed: `jq --version`
4. Run with verbose output: `bash -x ./burst.sh http://localhost:8080`

## 🛑 Graceful Shutdown

```bash
# Local
docker-compose down

# Render: Auto-sleeps after 15 min inactivity on free tier
# To manually stop: Go to dashboard → Settings → Delete service

# Railway/Fly: Use their dashboards to pause/stop
```

## 📈 Scaling Recommendations

### Local Development
- Works fine with docker-compose
- Database: SQLite or PostgreSQL in container

### Production (1K-10K concurrent users)
1. **Database:** PostgreSQL with streaming replication
2. **Connection pooling:** HikariCP (already configured)
3. **Read replicas:** For GET /shows/{id} queries
4. **CDN:** For static content
5. **Load balancer:** Distribute across app instances

### Large Scale (100K+ concurrent users)
1. **Database sharding:** By show_id
2. **Cache layer:** Redis for seat availability
3. **Message queue:** Kafka for events
4. **Microservices:** Separate show service, reservation service
5. **Observability:** ELK stack + Prometheus + Grafana

## 📝 CI/CD Setup

### GitHub Actions (Optional)

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to Render
on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Deploy to Render
        run: |
          curl https://api.render.com/deploy/srv-${{ secrets.RENDER_SERVICE_ID }} \
            -H "Content-Type: application/json" \
            -d '{"clearCache":"full"}' \
            -H "Authorization: Bearer ${{ secrets.RENDER_API_KEY }}"
```

Get API key from Render account settings.

---

**Questions?** Check README.md and WRITEUP.md for more details.
