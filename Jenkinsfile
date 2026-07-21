pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'cd starter_code && mvn -B package'
            }
        }

        stage('Test') {
            steps {
                sh 'cd starter_code && mvn -B test'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'starter_code/target/*.war', fingerprint: true
            }
        }
    }

    post {
        always {
            junit 'starter_code/target/surefire-reports/*.xml'
        }
    }
}
