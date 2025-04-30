def call(Map config = [:]) {
    String svcAccount = config.serviceAccountName ?: 'default'
    pipeline {
        agent {
            kubernetes {
                yaml '''
apiVersion: v1
kind: Pod
metadata:
  name: jenkins-agent
spec:
  serviceAccountName: ${svcAccount}
  containers:
    - name: maven
      image: maven:3.9.4-eclipse-temurin-17
      command: ['cat']
      tty: true
    - name: kubectl
      image: alpine/k8s:1.27.3
      command: ['cat']
      tty: true
'''.stripIndent()
                defaultContainer 'maven'
            }
        }
        environment {
            SONARQUBE_SCANNER_HOME = tool 'SonarQubeScanner'
            NEXUS_CREDENTIALS_ID = config.nexusCredentialsId ?: 'nexus-docker-creds'
            IMAGE_NAME = config.imageName
            IMAGE_TAG = config.imageTag
            GROUP_ID   = config.groupId
        }
        tools {
            maven 'M3'
        }
        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }
            stage('Build package') {
                steps {
                    withCredentials([usernamePassword(credentialsId: "${NEXUS_CREDENTIALS_ID}", usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
                        withMaven(mavenSettingsConfig: 'nexus-settings') {
                            sh 'mvn clean package'
                        }
                    }
                }
            }
            stage('SonarQube Analysis') {
                steps {
                    withSonarQubeEnv('SonarQube') {
                        sh "${SONARQUBE_SCANNER_HOME}/bin/sonar-scanner -Dsonar.projectKey=${IMAGE_NAME} -Dsonar.sources=src -Dsonar.java.binaries=target/classes"
                    }
                }
            }
            stage('Build Image') {
                steps {
                    withCredentials([usernamePassword(credentialsId: "${NEXUS_CREDENTIALS_ID}", usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
                        withMaven(mavenSettingsConfig: 'nexus-settings') {
                            sh 'mvn jib:build -DsendCredentialsOverHttp=true'
                        }
                    }
                }
            }
            stage('Deploy on Minikube') {
                steps {
                    container('kubectl') {
                        script {
                            sh 'kubectl apply -f src/main/resources/k8s/deployment.yaml'
                            def deploymentName = IMAGE_NAME
                            echo "Checking if deployment ${deploymentName} exists..."
                            def exists = sh(script: "kubectl get deployment ${deploymentName} --ignore-not-found", returnStdout: true).trim()
                            if (exists) {
                                echo "Deployment found. Forcing rollout restart..."
                                sh "kubectl rollout restart deployment/${deploymentName}"
                            } else {
                                echo "Deployment not found yet. Skipping rollout restart."
                            }
                        }
                    }
                }
            }
        }
        post {
            success {
                echo 'Build completata con successo!'
            }
            failure {
                echo 'Errore nella build.'
            }
        }
    }
}