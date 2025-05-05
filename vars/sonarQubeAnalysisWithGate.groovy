def call(Map config = [:]) {

    String server       = 'SonarQube'
    String scannerTool  = 'SonarQubeScanner'
    String projectKey   = config.projectKey
    String sources      = 'src'
    String binaries     = 'target/classes'


    try {
        // Run SonarQube scanner
        withSonarQubeEnv(server) {
            String scannerHome = tool(scannerTool)
            sh "${scannerHome}/bin/sonar-scanner " +
                    "-Dsonar.projectKey=${projectKey} " +
                    "-Dsonar.sources=${sources} " +
                    "-Dsonar.java.binaries=${binaries}"
        }
        echo "✅ SonarQube analysis completed for project '${projectKey}'."

        echo "⏳ Attendo generazione Quality Gate..."
        sleep(5)
        script {
            def qg = waitForQualityGate()
            if (qg.status == 'IN_PROGRESS') {
                error("⚠️ Quality Gate ancora in stato 'IN_PROGRESS'. Interrompo la pipeline.")
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