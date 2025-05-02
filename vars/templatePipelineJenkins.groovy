def call(Map config = [:]) {
    pipeline {
        agent {
            kubernetes {
                yaml libraryResource('k8s/jenkins-agent-pod.yaml')
                defaultContainer 'maven'
            }
        }
        environment {
            IMAGE_NAME             = "${config.imageName}"
            IMAGE_TAG              = "${config.imageTag}"
            GROUP_ID               = "${config.groupId}"
            MAVEN_SETTINGS_ID      = "nexus-settings"
            NEXUS_CREDENTIALS_ID   = "nexus-docker-creds"
            SONARQUBE_SCANNER_HOME = tool 'SonarQubeScanner'
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
                        configFileProvider([configFile(fileId: MAVEN_SETTINGS_ID, variable: 'MAVEN_SETTINGS')]) {
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
                    script {
                        buildImageWithJib(
                                nexusCredentialsId: "${NEXUS_CREDENTIALS_ID}",
                                mavenSettingsId: "${MAVEN_SETTINGS_ID}",
                                groupId: "${GROUP_ID}",
                                imageName: "${IMAGE_NAME}",
                                imageTag: "${IMAGE_TAG}"
                        )
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