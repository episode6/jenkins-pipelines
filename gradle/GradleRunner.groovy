/**
 * Builds, tests and collects reports on a gradle project
 *
 * Required plugins...
 * Pipeline Utility Steps Plugin: https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Utility+Steps+Plugin
 * HTML Publisher Plugin: https://wiki.jenkins-ci.org/display/JENKINS/HTML+Publisher+Plugin
 */
def version = '0.0.11'
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

def deploy(boolean onlyMainBranches = true) {
  stage('check-deploy') {
    def projectVersion = getProjectVersion()
    if (!projectVersion || projectVersion == "unspecified") {
      def err = "Could not read projectVersion for job: ${env.JOB_NAME}, deploy failed."
      notifier.notifyPushbullet(err)
      error(err)
      return
    }

    def branchName = env.BRANCH_NAME
    def gitTag = getGitTag()

    // THIS IS A HACK: we don't want to deploy git tags automatically on the normal 
    // branch jenkins jobs (this risks a redeploy for that release if there's not an 
    // extra commit on top of the branch). So to avoid it we bank on the fact that
    // release tags and release branches usually don't match (env.BRANCH_NAME is 
    // actually the name of the tag for jenkins tag jobs)
    def isGitTagJob = gitTag && branchName == gitTag

    def isSnapshot = projectVersion.contains("SNAPSHOT")
    def shouldDeploy = (!onlyMainBranches) ||
      (branchName == "master") ||
      (branchName == "main") ||
      (branchName == "develop") ||
      isGitTagJob

    if (shouldDeploy) {
      stage('run-deploy') {
        println "Deploying ${env.JOB_NAME} v${projectVersion}"
        runGradle("deploy", "deploy", false)
        if (!isSnapshot) {
          notifier.notifyPushbullet("Succesfully deployed ${env.JOB_NAME} v${projectVersion}, tag: ${gitTag}")
        }
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

def getGitTag() {
  sh 'git fetch origin --tags'
  sh 'git tag --points-at > __git_tag'
  return "${readFile("__git_tag")}".trim()
}

return this
