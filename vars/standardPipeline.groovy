def call(Map config) {
    pipeline {
        agent any
        stages {
          stage(Checkout) {
            steps
}
            stage('Build') {
                steps {
                    sh 'mvn clean package'
                }
            }
            stage('Deploy to Dev') {
                steps {
                    // Using config.keyname to access variables from Jenkinsfile
                    deploy adapters: [tomcat9(credentialsId: config.devCreds, url: config.devUrl)], 
                           contextPath: 'dev-app', 
                           war: 'target/*.war'
                }
            }
            stage('Approval') {
                steps {
                    input message: "Promote build to Production?"
                }
            }
            stage('Deploy to Prod & Nexus') {
                steps {
                    parallel(
                        "Production": {
                            deploy adapters: [tomcat9(credentialsId: config.prodCreds, url: config.prodUrl)], 
                                   contextPath: 'prod-app', 
                                   war: 'target/*.war'
                        },
                        "Nexus": {
                            nexusArtifactUploader(
                                nexusVersion: 'nexus3',
                                protocol: 'http',
                                nexusUrl: config.nexusUrl.replace('http://', ''), // Strip protocol for plugin
                                groupId: config.groupId,
                                version: config.version,
                                repository: 'maven-releases',
                                credentialsId: config.nexusCreds,
                                artifacts: [[artifactId: config.artifactId, classifier: '', file: "target/${config.artifactId}.war", type: 'war']]
                            )
                        }
                    )
                }
            }
        }
    }
}

