# Jenkins EC2 Deployment

This pipeline assumes Jenkins runs on the EC2 instance that will host the app.

## EC2 prerequisites

Install Docker, Docker Compose, Git, and Jenkins on the EC2 instance. The pipeline runs the app build/test inside a JDK 21 Docker container, so the Jenkins host itself does not need a separate project JDK.

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

## Repository setup on Jenkins

Create a Pipeline job and connect it to this GitHub repository. Use `Jenkinsfile` from SCM.

## Jenkins credentials

Create these Jenkins `Secret text` credentials. The pipeline writes them into `.env` during deploy.

- `feedbackme-mysql-root-password`
- `feedbackme-mysql-password`
- `feedbackme-gemini-api-key`
- `feedbackme-grafana-admin-password`

For local manual deployment, copy `.env.example` to `.env` and fill the values.

```bash
cp .env.example .env
vi .env
```

## Ports

Open these inbound ports only as needed:

- `8080`: Spring Boot app
- `3000`: Grafana
- `9090`: Prometheus, preferably restrict to your IP
- `22`: SSH

Do not expose MySQL `3306` or Redis `6379` publicly unless there is a specific reason.
