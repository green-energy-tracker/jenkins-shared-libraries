def call(Map config) {
    withCredentials([usernamePassword(
            credentialsId: config.nexusCredentialsId,
            usernameVariable: 'NEXUS_USERNAME',
            passwordVariable: 'NEXUS_PASSWORD'
    )]) {
        configFileProvider([configFile(
                fileId: config.mavenSettingsId,
                variable: 'MAVEN_SETTINGS'
        )]) {
            sh """
                    mvn jib:build -DsendCredentialsOverHttp=true \
                    --settings \$MAVEN_SETTINGS 
                """
        }
    }
}