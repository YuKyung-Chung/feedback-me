pipeline {
    agent any

    environment {
        APP_IMAGE = 'feedbackme-app'
        APP_VERSION = "${env.BUILD_NUMBER}"
        COMPOSE_FILE = 'docker-compose.prod.yml'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh '''
                    docker run --rm \
                        -u "$(id -u):$(id -g)" \
                        -e HOME=/workspace \
                        -e GRADLE_USER_HOME=/workspace/.gradle \
                        -v "$PWD/feedbackme:/workspace" \
                        -w /workspace \
                        eclipse-temurin:21-jdk \
                        sh -c "chmod +x ./gradlew && ./gradlew clean test --no-daemon"
                '''
            }
        }

        stage('Build Image') {
            steps {
                sh 'docker build -t ${APP_IMAGE}:${APP_VERSION} -t ${APP_IMAGE}:latest ./feedbackme'
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                    string(credentialsId: 'feedbackme-mysql-root-password', variable: 'MYSQL_ROOT_PASSWORD'),
                    string(credentialsId: 'feedbackme-mysql-password', variable: 'MYSQL_PASSWORD'),
                    string(credentialsId: 'feedbackme-gemini-api-key', variable: 'GEMINI_API_KEY'),
                    string(credentialsId: 'feedbackme-grafana-admin-password', variable: 'GRAFANA_ADMIN_PASSWORD')
                ]) {
                    sh '''
                        set +x
                        cat > .env <<EOF
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
MYSQL_PASSWORD=${MYSQL_PASSWORD}
GEMINI_API_KEY=${GEMINI_API_KEY}
GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
APP_VERSION=${APP_VERSION}
EOF
                        chmod 600 .env
                        set -x
                        APP_VERSION=${APP_VERSION} docker compose -f ${COMPOSE_FILE} up -d
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f || true'
        }
    }
}
