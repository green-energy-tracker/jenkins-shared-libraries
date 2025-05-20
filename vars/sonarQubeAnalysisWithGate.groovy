def call(Map config = [:]) {

    String server       = 'SonarQube'
    String scannerTool  = 'SonarQubeScanner'
    String projectKey   = config.projectKey
    String sourcesProd  = 'src/main/java'
    String sourcesTest  = 'src/test/java'
    String binariesProd = 'target/classes'
    String binariesTest = 'target/test-classes'
    String coverageReport = 'target/site/jacoco/jacoco.xml'

    try {
        withSonarQubeEnv(server) {
            String scannerHome = tool(scannerTool)
            sh "${scannerHome}/bin/sonar-scanner " +
                    "-Dsonar.projectKey=${projectKey} " +
                    "-Dsonar.sources=${sourcesProd} " +
                    "-Dsonar.tests=${sourcesTest} " +
                    "-Dsonar.java.binaries=${binariesProd} " +
                    "-Dsonar.java.test.binaries=${binariesTest}" +
                    "-Dsonar.coverage.jacoco.xmlReportPaths=${coverageReport}"
        }
        echo "✅ SonarQube analysis completed for project '${projectKey}'."

        echo "⏳ Waiting Quality Gate..."
//        sleep(30)
//        script {
//            def qg = waitForQualityGate()
//            if (qg.status == 'IN_PROGRESS') {
//                error("⚠️ Quality Gate status 'IN_PROGRESS'. Aborting pipeline.")
//            } else if (qg.status != 'OK') {
//                error("❌ SonarQube Quality Gate failed with status: ${qg.status}")
//            } else {
//                echo "✅ SonarQube Quality Gate passed: ${qg.status}"
//            }
//        }
    } catch (err) {
        error("❌ SonarQube analysis failed for project '${projectKey}': ${err}")
    }
}