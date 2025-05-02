def call(Map config = [:]) {
    String mavenSettingsId = config.mavenSettingsConfig

    pipeline {
        agent {
            kubernetes {
                yaml '''
apiVersion: v1
kind: Pod
metadata:
  name: jenkins-agent
spec:
  serviceAccountName: jenkins-agent
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
            NEXUS_CREDENTIALS_ID   = "${config.nexusCredentialsId}"
            IMAGE_NAME             = "${config.imageName}"
            IMAGE_TAG              = "${config.imageTag}"
            GROUP_ID               = "${config.groupId}"
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
                        configFileProvider([configFile(fileId: mavenSettingsId, variable: 'MAVEN_SETTINGS')]) {
                            sh "mvn clean package --settings $MAVEN_SETTINGS"
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
                        configFileProvider([configFile(fileId: mavenSettingsId, variable: 'MAVEN_SETTINGS')]) {
                            sh "mvn compile jib:build -DsendCredentialsOverHttp=true --settings $MAVEN_SETTINGS"
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