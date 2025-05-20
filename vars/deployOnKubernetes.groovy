
def call(Map config = [:]) {
    def imageName = config.imageName ?: error("Missing parameter 'imageName'")

    container('kubectl') {
        script {
            sh "kubectl apply -f src/main/resources/k8s/deployment.yaml"

            echo "Checking if deployment '${imageName}' exists..."
            def exists = sh(
                    script: "kubectl get deployment ${imageName} --ignore-not-found",
                    returnStdout: true
            ).trim()

            if (exists) {
                echo "Deployment found. Forcing rollout restart of '${imageName}'..."
                sh "kubectl rollout restart deployment/${imageName}"
            } else {
                echo "Deployment '${imageName}' not found yet. Skipping rollout restart."
            }
        }
    }
}