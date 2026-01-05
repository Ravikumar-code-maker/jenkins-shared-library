def call(Map config) {
    pipeline {
      agent any
      stages {
        stage(Checkout) {
          steps {
            checkout scmGit(branches: [[name: '*/main']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/Ravikumar-code-maker/jenkins-shared-library']])
          }
        }
        stage(Build) {
          steps {
            sh "mvn clean package"
          }
        }
        stage('Deploy to Dev') {
          steps {
            deployToTomcat(config.devUrl, config.devCreds, 'dev-context')
          }
        }
        stage('Manual Promotion') {
          steps {
            input message: "Promote build ${env.BUILD_NUMBER} to production"
          }
        }
        stage('Deploy To Prod') {
          steps {
              parallel(
                  "Production": {
                      deployToTomcat(config.prodUrl, config.prodCreds, 'prod-context')
                  },
                  "Nexus": {
                      nexusArtifactUploader(
                          nexusVersion: 'nexus3', protocol: 'http',
                          nexusUrl: config.nexusUrl, groupId: config.groupId,
                          version: config.version, repository: 'maven-releases',
                          credentialsId: config.nexusCreds,
                          artifacts: [[artifcatId: config.artifactId, classifier: '', file: "target/${config.artifactId}.war", type: 'war']]
                      )
                  }
              )
          }
        }
        
      }
    }
}

def deployToTomcat(url, creds, path) {
    deploy adapters: [tomcat9(credentilasId: creds, url: url)], contextPath: path, war: 'target/*.war'
}
