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
        }
        stages {
            stage('Checkout code') {
                steps {
                    checkout scm
                }
            }
            stage('Compile & Test') {
                steps {
                    withCredentials([usernamePassword(credentialsId: "${NEXUS_CREDENTIALS_ID}", usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
                        configFileProvider([configFile(fileId: MAVEN_SETTINGS_ID, variable: 'MAVEN_SETTINGS')]) {
                            sh "mvn clean verify --settings $MAVEN_SETTINGS"
                        }
                    }
                }
            }
            stage('Code Quality Analysis') {
                steps {
                    script {
                      sonarQubeAnalysis(projectKey: IMAGE_NAME)
                    }
                }
            }
            stage('Build & Push Image') {
                steps {
                    script {
                        buildImageWithJib( groupId: "${GROUP_ID}", imageName: "${IMAGE_NAME}", imageTag: "${IMAGE_TAG}" )
                    }
                }
            }
            stage('Deploy to Kubernetes') {
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