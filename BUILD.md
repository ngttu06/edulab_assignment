# EduLab KHTN8 - Build Guide

## Docker Build

### Latest Build Info

**Image:** `edulab-khtn8:latest`
**Tagged:** `edulab-khtn8:main-fe5a55a-20261028T160323Z`
**Image ID:** `e96cfdaf211e`
**Size:** 672 MB (225 MB compressed)
**Build Time:** ~2 minutes (77 seconds for Maven build)
**Base Images:**
- Build: `maven:3.9.9-eclipse-temurin-21`
- Runtime: `eclipse-temurin:21-jre-jammy`

### Build Command

```bash
docker build -t edulab-khtn8:latest .
```

### Tag with Version

```bash
COMMIT_HASH=$(git rev-parse --short HEAD)
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
docker tag edulab-khtn8:latest edulab-khtn8:main-${COMMIT_HASH}-${TIMESTAMP}
```

### Run Locally

**With Docker Compose (recommended):**
```bash
docker compose -f compose.local.yml up -d
```

**Standalone (for testing):**
```bash
docker run -d \
  --name edulab-test \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/edulab_assignment \
  -e SPRING_DATASOURCE_USERNAME=edulab \
  -e SPRING_DATASOURCE_PASSWORD=edulab_local_password \
  -e EDULAB_COOKIE_SECURE=false \
  edulab-khtn8:latest
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `remote` | Spring profile (use `local` for dev) |
| `EDULAB_SERVER_PORT` | `8080` | Application port |
| `EDULAB_UPLOAD_ROOT` | `/var/lib/edulab/storage` | Upload directory |
| `EDULAB_COOKIE_SECURE` | `true` | Require HTTPS for cookies |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70.0` | JVM options |

### Build Contents

**Maven Build:**
- 120 Java source files compiled
- Spring Boot WAR with embedded Tomcat
- Database migration scripts packaged
- Static assets (CSS, JS, JSP templates)

**Runtime Container:**
- Non-root user `edulab:edulab` (UID/GID 10001)
- Health check endpoint on port 8080
- Storage volume at `/var/lib/edulab/storage`

### Recent Changes (fe5a55a)

This build includes the redesigned shell layout:
- Responsive header with role badges and shortcut labels
- Role-aware sidebar navigation (Student/Teacher/Admin/Parent)
- Modern CSS foundation with tonal palette and fluid typography
- Grid-first layout tokens and component styles
- Complete responsive breakpoints (1279px, 991px, 767px, 479px)

See commits:
- `f921182` - Redesign shell layout with role-aware sidebar
- `fe5a55a` - Add responsive breakpoints for all components

### Verification

**Check container health:**
```bash
docker ps --filter name=edulab
docker logs edulab-test --tail 50
```

**Test endpoint:**
```bash
curl http://localhost:8080/actuator/health
```

**Connect to database:**
```bash
docker exec -it edulab-mysql-local mysql -uedulab -p edulab_assignment
```

---

**Build Date:** 2026-10-28
**Last Updated:** After commit fe5a55a
