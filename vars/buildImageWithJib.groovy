def call(Map config) {
    withCredentials([usernamePassword(credentialsId: "${NEXUS_CREDENTIALS_ID}", usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
        configFileProvider([configFile(fileId: MAVEN_SETTINGS_ID, variable: 'MAVEN_SETTINGS')]) {
            def IMAGE_JIB_VERSION = 'openjdk:17-jdk-slim'
            def JIB_MAVEN_PLUGIN_VERSION = '3.4.5'
            def REGISTRY_URL = 'nexus-registry.nexus.svc.cluster.local:64395'
            sh """
                    mvn com.google.cloud.tools:jib-maven-plugin:${JIB_MAVEN_PLUGIN_VERSION}:build \
                    --settings \$MAVEN_SETTINGS \
                    -Djib.from.image=${IMAGE_JIB_VERSION} \
                    -Djib.to.image=${REGISTRY_URL}/${config.groupId}/${config.imageName}:${config.imageTag} \
                    -Djib.to.auth.username=\$NEXUS_USERNAME \
                    -Djib.to.auth.password=\$NEXUS_PASSWORD \
                    -Djib.allowInsecureRegistries=true \
                    -DsendCredentialsOverHttp=true
                """
        }
    }
}