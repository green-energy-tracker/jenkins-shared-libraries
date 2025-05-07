def call(Map config = [:]) {

    String server       = 'SonarQube'
    String scannerTool  = 'SonarQubeScanner'
    String projectKey   = config.projectKey
    String sources      = 'src'
    String binaries     = 'target/classes'


    try {
        withSonarQubeEnv(server) {
            String scannerHome = tool(scannerTool)
            sh "${scannerHome}/bin/sonar-scanner " +
                    "-Dsonar.projectKey=${projectKey} " +
                    "-Dsonar.sources=${sources} " +
                    "-Dsonar.java.binaries=${binaries}"
        }
        echo "✅ SonarQube analysis completed for project '${projectKey}'."

        echo "⏳ Waiting Quality Gate..."
        script {
            def qg
            def maxRetries = 10
            def retryCount = 0
            def delayBetweenChecks = 10

            while (retryCount < maxRetries) {
                qg = waitForQualityGate()

                if (qg.status == 'OK') {
                    echo "✅ SonarQube Quality Gate passed: ${qg.status}"
                    break
                } else if (qg.status == 'ERROR') {
                    error("❌ SonarQube Quality Gate failed with status: ${qg.status}")
                    break
                } else if (qg.status == 'IN_PROGRESS') {
                    echo "⚠️ Quality Gate status 'IN_PROGRESS'. Retrying in ${delayBetweenChecks} seconds..."
                    retryCount++
                    sleep(delayBetweenChecks)
                } else {
                    error("❌ Unknown Quality Gate status: ${qg.status}")
                }
            }
            if (retryCount == maxRetries) {
                error("❌ Maximum retries reached. Quality Gate is still IN_PROGRESS after ${maxRetries} attempts.")
            }
        }
    } catch (err) {
        error("❌ SonarQube analysis failed for project '${projectKey}': ${err}")
    }
}