/**
 * Builds, tests and collects reports on a gradle project
 *
 * Required plugins...
 * Pipeline Utility Steps Plugin: https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Utility+Steps+Plugin
 * HTML Publisher Plugin: https://wiki.jenkins-ci.org/display/JENKINS/HTML+Publisher+Plugin
 */
def version = '0.0.1'

def buildAndTest() {
  stage('build') {
    sh "./gradlew clean assemble"
  }

  stage('test') {
    runTests('test')
  }
}

def runTests(String stageName) {
  Exception err
  try {
    sh "./gradlew check"
  } catch (Exception e) {
    err = e
    currentBuild.result = "FAILURE"
  } finally {
    notifyFailure(stageName, err)
    collectReports()
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

def notifyFailure(String stageName, Exception ex) {
  if (!env.PUSHBULLET_USER_KEY || !env.PUSHBULLET_API_KEY) {
    return
  }

  String message = "Job Failed: ${env.JOB_NAME} Stage: ${stageName}"
  if (ex) {
    message = "${message}\n${ex.getMessage()}"
  }
  sh "curl " +
      "-F \"token=${env.PUSHBULLET_API_KEY}\" " +
      "-F \"user=${env.PUSHBULLET_USER_KEY}\" " +
      "-F \"message=${message}\" " +
      "-F \"url=${env.BUILD_URL}\" " +
      "-F \"url_title=Open Build\" " +
      "https://api.pushover.net/1/messages.json"
}

return this