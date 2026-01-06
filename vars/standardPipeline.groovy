def call(Map config) {
    pipeline {
        agent any
        stages {
            stage('Build') {
                steps {
                    sh 'mvn clean package'
                }
            }
            stage('Deploy to Dev') {
                steps {
                    deployToTomcat(config.devUrl, config.devCreds, 'dev-context') 
                           
                }
            }
            stage('Manual Promotion') {
                steps {
                    input message: "Promote build ${env.BUILD_NUMBER} to Production?"
                }
            }
           stage('Deploy to Prod & Nexus') {
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
                                artifacts: [[artifactId: config.artifactId, classifier: '', file: "target/${config.artifactId}.war", type: 'war']]
                            )
                        }
                    )
                }
            }
        }
    }
}
//def deployToTomcat(url, creds, path) {
//    deploy adapters: [tomcat9(credentialsId: creds, url: url)], contextPath: path, war: 'target/*.war'
//}

def deployToTomcat(url, creds, path) {
    // We use withCredentials to securely get the username and password
    withCredentials([usernamePassword(credentialsId: creds, passwordVariable: 'PASS', usernameVariable: 'USER')]) {
        echo "Deploying to ${url}..."
        sh """
            curl -v -u "${USER}:${PASS}" \
                 -T "target/web-app.war" \
                 "${url}/manager/text/deploy?path=/${path}&update=true"
        """
    }
}


//In 2026, this script-based approach is the Organization-Level Standard because it is more reliable than Jenkins plugins. Here is the line-by-line explanation of how it works:
//1. def deployToTomcat(url, creds, path)
//Purpose: This defines a reusable function (method).
//:
// url: The Tomcat address (e.g., http://107.178.223.130:8080).
// creds: The Jenkins Credentials ID (e.g., tomcat-dev-user).
// path: The context path for your app (e.g., dev-context).
// 2. withCredentials([usernamePassword(...)])
// Purpose: This is a security wrapper.
// How it works: It goes into the Jenkins Credentials Store, finds the ID you provided (creds), and extracts the secret username and password.
// Variables: It assigns the username to ${USER} and the password to ${PASS}. These variables only exist inside this block and are masked (hidden) in the Jenkins logs with **** for security.
// 3. sh """ ... """
// Purpose: This tells Jenkins to execute a Shell command on the build agent's terminal.
// Triple Quotes ("""): These allow you to write a command across multiple lines and use Groovy variables (like ${url}) directly inside the shell script.
// 4. curl -v -u "${USER}:${PASS}"
// curl: A command-line tool used to transfer data over networks.
// -v (Verbose): Crucial for troubleshooting. This tells curl to print everything it does. If you get a 401 Unauthorized, this will show you exactly what Tomcat said back to Jenkins.
// -u "${USER}:${PASS}": This handles Basic Authentication. It sends the username and password to Tomcat to prove Jenkins has permission to deploy.
// 5. -T "target/web-app.war"
// -T (Transfer/Upload): This tells curl to "PUT" or upload a local file to the server.
// "target/web-app.war": This is the file Maven just created. Jenkins will look in the workspace's target folder for this specific file.
// 6. "${url}/manager/text/deploy?path=/${path}&update=true"
// This is the Tomcat Manager API URL. It is the most important part:
// /manager/text: This tells Tomcat you are using the Text Manager (the "machine" interface) rather than the "GUI" (human) interface. This requires the manager-script role.
// /deploy: The specific API command to install an application.
// ?path=/${path}: Tells Tomcat what to name the app. If path is dev-context, your app will be at http://ip:8080/dev-context.
// &update=true: Highly Recommended for 2026. This tells Tomcat: "If an old version of this app is already there, overwrite it." Without this, the second time you run the job, it would fail because the app already exists.
// Summary of Workflow
// Security: Jenkins pulls the hidden password.
// Action: curl picks up the .war file from the build folder.
// Authentication: curl sends the credentials to the Tomcat API.
// Deployment: Tomcat receives the file, unpacks it, and starts your application.
// Why this is better than the "Deploy" plugin:
// If the plugin fails, it just gives a generic "ContainerException." If this curl command fails, the -v flag shows you the exact HTTP response from Tomcat (e.g., 401 Unauthorized, 403 Forbidden, or 404 Not Found), making it much easier to fix.




