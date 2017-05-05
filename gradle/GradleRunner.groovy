/**
 * Builds, tests and collects reports on a gradle project
 *
 * Required plugins...
 * Pipeline Utility Steps Plugin: https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Utility+Steps+Plugin
 * HTML Publisher Plugin: https://wiki.jenkins-ci.org/display/JENKINS/HTML+Publisher+Plugin
 */
def version = '0.0.3'

def buildAndTest() {
  stage('build') {
    runGradle("build", "clean assemble", false)
  }

  stage('test') {
    runGradle("test", "check", true)
  }
}

def maybeDeploy() {
  stage('deploy') {
    def projectVersion = getProjectVersion()
    def branchName = env.BRANCH_NAME
    def isSnapshot = projectVersion.contains("SNAPSHOT")

    if ((branchName == "master" && !isSnapshot) || (branchName == "develop" && isSnapshot)) {
      println "Deploying ${env.JOB_NAME} v${projectVersion}"
      runGradle("deploy", "deploy", false)
      notifyPushbullet("Succesfully deployed ${env.JOB_NAME} v${projectVersion}")
    } else {
      println "Skipping deploy of ${env.JOB_NAME} v${projectVersion}"
    }
  }
}

def runGradle(String stageName, String execStr, boolean shouldCollectReports) {
  Exception err
  try {
    sh "./gradlew ${execStr} > ${outputLogFilename(stageName)}"
  } catch (Exception e) {
    err = e
    currentBuild.result = "FAILURE"
  } finally {
    sh "cat ${outputLogFilename(stageName)}"
    if (err) {
      notifyFailure(stageName)
    }
    if (shouldCollectReports) {
      collectReports()
    }
    if (err) {
      throw err
    }
  }
}

def collectReports() {
  junit(
      keepLongStdio: true,
      testResults: '**/build/test-results/**/*.xml')
  collectHtmlReports(glob: "**/build/reports/**/index.html")
}

def collectHtmlReports(Map findIndexFilesParams) {
  def htmlIndexFiles = findFiles(findIndexFilesParams)
  for (int i = 0; i < htmlIndexFiles.length; i++) {
    def it = htmlIndexFiles[i]
    def path = it.path.substring(0, it.path.length() - "/index.html".length())
    def name = path.substring(0, path.indexOf("/"))
    def prefix = path.substring(path.lastIndexOf("/") + 1, path.length())
    if (name != "build") {
      prefix = "${name} ${prefix}"
    }
    publishHTML([
        allowMissing: false,
        alwaysLinkToLastBuild: true,
        keepAll: false,
        reportDir: path,
        reportFiles: 'index.html',
        reportName: "${prefix} report (${i})"])
  }
}

def notifyFailure(String stageName) {
  String message = "Job Failed: ${env.JOB_NAME}\nBuild #${env.BUILD_NUMBER}\nStage: ${stageName}"
  def outputLogs = readFile(outputLogFilename(stageName)).tokenize("\n")
  for (int i = 0; i < outputLogs.size(); i++) {
    String logLine = outputLogs[i]
    if (logLine.contains("FAILED")) {
      message = "${message}\n\n${logLine}"
    }
  }
  notifyPushbullet(message)
}

def notifyPushbullet(String message) {
  if (!env.PUSHBULLET_USER_KEY || !env.PUSHBULLET_API_KEY) {
    return
  }

  try {
    sh "curl " +
        "-F \"token=${env.PUSHBULLET_API_KEY}\" " +
        "-F \"user=${env.PUSHBULLET_USER_KEY}\" " +
        "-F \"message=${message}\" " +
        "-F \"url=${env.JOB_URL}\" " +
        "-F \"url_title=Open Build\" " +
        "https://api.pushover.net/1/messages.json"
  } catch (Exception e) {
    // fail quietly, since we've already failed the build by this point
    println "Failed to send pushover notification: ${e.getMessage()}"
  }
}

def outputLogFilename(String stageName) {
  return "${stageName}-output-log"
}

def getProjectVersion() {
  sh './gradlew properties | grep -o \'^version: .*$\' | sed \'s/version: //\' > __gradle_project.version'
  return "${readFile("__gradle_project.version")}"
}

return this
