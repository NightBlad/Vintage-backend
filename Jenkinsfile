pipeline {
    agent any

    environment {
        GIT_USERNAME = 'NightBlad'
        GIT_CREDENTIAL = credentials('KeyCred')
        GIT_REPO = 'https://github.com/DaoTienDat2304/Vintage'
        VERSION = "v0.${BUILD_NUMBER}"
        SONAR_PROJECT_KEY = 'vintage123456'
    }

    stages {
        stage('Check Source') {
            steps {
                echo 'First Stage'
                git url: "${GIT_REPO}", branch: 'master', credentialsId: 'KeyCred'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                script {
                    def mvn = tool 'Maven'
                    withSonarQubeEnv('SonarQube') {
                        bat "${mvn}/bin/mvn clean verify -DskipTests=true sonar:sonar -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.projectName=vintage"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    withDockerRegistry(
                        credentialsId: 'docker',
                        url: 'https://index.docker.io/v1/'
                    ) {
                        //Remove the container
                        bat 'docker-compose down'
                        // Check the docker-compose version
                        bat 'docker-compose --version'
                        // Bring up the services
                        bat 'docker-compose up -d'
                        // Ensure the services are running
                        bat 'docker-compose ps'
                    }
                }
            }
        }

    }
}

