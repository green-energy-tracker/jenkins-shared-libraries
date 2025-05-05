def call(Map config = [:]) {

    String server       = 'SonarQube'
    String scannerTool  = 'SonarQubeScanner'
    String projectKey   = config.projectKey
    String sources      = 'src'
    String binaries     = 'target/classes'
    int timeoutMin      = 5
    int maxRetries      = (timeoutMin * 60) / 30
    int sleepingTime    = 30000

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

        int retry = 0
        def qg = null
        timeout(time: timeoutMin, unit: 'MINUTES') {
            script {
                while (retry < maxRetries) {
                    try {
                        qg = waitForQualityGate()
                        if (qg.status == 'IN_PROGRESS') {
                            echo "⏳ Quality Gate still in progress... retrying in 30 seconds"
                            sleep(sleepingTime)
                            retry++
                        } else {
                            break
                        }
                    } catch (err) {
                        echo "⚠️ waitForQualityGate() failed on attempt ${retry + 1}: ${err}"
                        sleep(sleepingTime)
                        retry++
                    }
                }

                if (!qg || qg.status == 'IN_PROGRESS') {
                    error("❌ Quality Gate timeout: status still IN_PROGRESS after ${timeoutMin} minutes.")
                } else if (qg.status != 'OK') {
                    error("❌ SonarQube Quality Gate failed: ${qg.status}")
                } else {
                    echo "✅ SonarQube Quality Gate passed: ${qg.status}"
                }
            }
        }
    } catch (err) {
        error("❌ SonarQube analysis failed for project '${projectKey}': ${err}")
    }
}