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
            deplyToTomcat(config.prodUrl, config.prodCreds, 'prod-context')
          }
        }
        
      }
    }
}
