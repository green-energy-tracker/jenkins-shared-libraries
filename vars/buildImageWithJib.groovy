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
                    mvn com.google.cloud.tools:jib-maven-plugin:${JIB_MAVEN_PLUGIN_VERSION}:build \
                    --settings \$MAVEN_SETTINGS 
                """
        }
    }
}