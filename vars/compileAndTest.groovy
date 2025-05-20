def call() {
    withCredentials([usernamePassword(credentialsId: "${NEXUS_CREDENTIALS_ID}", usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
        configFileProvider([configFile(fileId: MAVEN_SETTINGS_ID, variable: 'MAVEN_SETTINGS')]) {
            sh "mvn clean verify --settings $MAVEN_SETTINGS"
        }
    }
}