pipeline {
    agent { label 'docker' }
    
    tools {
        maven 'Maven3'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B package'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/*.war', fingerprint: true
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t auth-course:latest .'
            }
        }

        stage('Deploy') {
            steps {
                sh 'docker run -d --name auth-course -p 8081:8081 auth-course:latest'
            }
        }
    }

    post {
        always {
            junit 'target/surefire-reports/*.xml'
        }
    }
}
