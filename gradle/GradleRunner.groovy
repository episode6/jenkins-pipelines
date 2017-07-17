/**
 * Builds, tests and collects reports on a gradle project
 *
 * Required plugins...
 * Pipeline Utility Steps Plugin: https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Utility+Steps+Plugin
 * HTML Publisher Plugin: https://wiki.jenkins-ci.org/display/JENKINS/HTML+Publisher+Plugin
 */
def version = '0.0.6'
notifier = load("common/Notifier.groovy")
runner = load("common/Runner.groovy")

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
    if (!projectVersion || projectVersion == "unspecified") {
      def err = "Could not read projectVersion for job: ${env.JOB_NAME}, deploy failed."
      notifier.notifyPushbullet(err)
      error(err)
      return
    }

    def branchName = env.BRANCH_NAME
    def isSnapshot = projectVersion.contains("SNAPSHOT")

    if ((branchName == "master" && !isSnapshot) || (branchName == "develop" && isSnapshot)) {
      println "Deploying ${env.JOB_NAME} v${projectVersion}"
      runGradle("deploy", "deploy", false)
      if (!isSnapshot) {
        notifier.notifyPushbullet("Succesfully deployed ${env.JOB_NAME} v${projectVersion}")
      }
    } else {
      println "Skipping deploy of ${env.JOB_NAME} v${projectVersion}"
    }
  }
}

def runGradle(String stageName, String execStr, boolean shouldCollectReports) {
  try {
    runner.runStagedCommand(stageName, "./gradlew --no-daemon ${execStr}")
  } finally {
    if (shouldCollectReports) {
      collectReports()
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

def getProjectVersion() {
  sh './gradlew properties | grep -o \'^version: .*$\' | sed \'s/^version: //\' > __gradle_project.version'
  return "${readFile("__gradle_project.version")}".trim()
}

return this
