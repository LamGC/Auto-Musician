pipeline {
    agent {
        node {
            label 'lang:java && java:11'
        }
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    stages {
        stage('Initialize project...') {
            steps {
                echo 'Set build script permissions...'
                sh "chmod +x ./gradlew"
                echo 'Check build script status...'
                sh './gradlew --version'
                echo 'Check passed.'
            }
        }

        stage('Build project') {
            steps {
                echo 'Building..'
                sh "./gradlew build"
                echo 'Collection component...'
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                archiveArtifacts artifacts: 'build/distributions/**'
                echo 'Build succeed.'
            }
        }
    }
}