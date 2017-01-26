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
    runTests()
  }
}

def runTests() {
  Exception err
  try {
    sh "./gradlew check"
  } catch (Exception e) {
    err = e
    currentBuild.result = "FAILURE"
  } finally {
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

return this