def call(Map config = [:]) {

    String server       = 'SonarQube'
    String projectKey   = config.projectKey

    try {
        withSonarQubeEnv(server) {
            withCredentials([usernamePassword(credentialsId: "${NEXUS_CREDENTIALS_ID}", usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
                configFileProvider([configFile(fileId: MAVEN_SETTINGS_ID, variable: 'MAVEN_SETTINGS')]) {
                    sh "mvn sonar:sonar  --settings $MAVEN_SETTINGS"
                }
            }

        }
        echo "✅ SonarQube analysis completed for project '${projectKey}'."

        echo "⏳ Waiting Quality Gate..."
        sleep(45)
        script {
            def qg = waitForQualityGate()
            if (qg.status == 'IN_PROGRESS') {
                error("⚠️ Quality Gate status 'IN_PROGRESS'. Aborting pipeline.")
            } else if (qg.status != 'OK') {
                error("❌ SonarQube Quality Gate failed with status: ${qg.status}")
            } else {
                echo "✅ SonarQube Quality Gate passed: ${qg.status}"
            }
        }
    } catch (err) {
        error("❌ SonarQube analysis failed for project '${projectKey}': ${err}")
    }
}