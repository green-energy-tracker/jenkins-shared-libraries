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
            stage('SonarQube Analysis & Quality Gate') {
                steps {
                    script {
                      sonarQubeAnalysisWithGate(projectKey: IMAGE_NAME)
                    }
                }
            }
            stage('Build Image and Deploy on Nexus Registry') {
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
                    deployOnKubernetes(imageName: "${IMAGE_NAME}")
                }
            }
        }
        post {
            success {
                echo '✅ Pipeline completed successfully!'
            }
            failure {
                echo '❌ Pipeline failed.'
            }
        }
    }
}