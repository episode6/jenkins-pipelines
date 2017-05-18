/**
 * Builds and test go projects.
 *
 * Required Plugins...
 * Go Plugin: https://wiki.jenkins-ci.org/display/JENKINS/Go+Plugin
 *
 * For this to work you must checkout your project in a sub-directory of 'src'
 * under your normal working dir. Then pass this srcDir to the goRunner's methods
 * Usage Example:
 *
 * <pre>{@code
 * // define srcDir and Go Name
 * def srcDir = 'src/github.com/episode6/go-example'
 * def goName = 'Go 1.8.1'
 *
 * stage('checkout') {
 *   dir(srcDir) { // checkout scm in srcDir
 *     checkout scm
 *     sh 'git submodule update --init'
 *   }
 * }
 *
 * // get GoRunner
 * def goRunner
 * stage('pipeline') {
 *   goRunner = fileLoader.fromGit(
 *       'go/GoRunner',
 *       'git@github.com:episode6/jenkins-pipelines.git',
 *       'develop',
 *       null,
 *       '')
 * }
 *
 * // pass in srcDir and goName to GoRunner methods
 * goRunner.prepare(srcDir, goName, [
 *   "go get -u github.com/jteeuwen/go-bindata/...",
 *   "go generate"])
 * goRunner.buildAndTest(srcDir, goName)
 *
 * }</pre>
 */
def version = '0.0.6'
notifier = load("common/Notifier.groovy")
runner = load("common/Runner.groovy")


def prepare(String srcDir, String goName, List<String> prepareCommands) {
  stage('prepare') {
    runGoCmds("prepare", srcDir, goName, prepareCommands)
  }
}

def buildAndTest(String srcDir, String goName, String testTimeout = "10m") {
  stage('build') {
    runGoCmds("build", srcDir, goName, [
      "go get ./...",
      "go build ./...",
      "go install ./..."])
  }
  stage('test_prep') {
    runGoCmds("test_prep", srcDir, goName, ["go get -u github.com/jstemmer/go-junit-report"])
  }
  stage('test') {
    runGoCmds("test", srcDir, goName, ["go test -v -timeout ${testTimeout} -cover ./..."], true)
  }
}

def runGoCmds(String stageName, String srcDir, String goName, List<String> goCmds, boolean collectReports = false) {
  def goRoot = tool name: goName, type: 'go'
  def goPath = pwd()
  def binPath = "${goRoot}/bin:${goPath}/bin"
  if (env.GOPATH) {
    goPath = "${env.GOPATH}:${goPath}"
    binPath = "${binPath}:${env.GOPATH/bin}"
  }
  withEnv(["GOROOT=${goRoot}", "GOPATH=${goPath}", "PATH+GO=${binPath}"]) {
    dir(srcDir) {
      try {
        runner.runStagedCommands(stageName, goCmds)
      } finally {
        if (collectReports) {
          def logFileName = runner.getLogFileForStage(stageName)
          sh "cat ${logFileName} | go-junit-report > go_test_report.xml"
          junit(
              keepLongStdio: true,
              testResults: '**/go_test_report.xml')
        }
      }
    }
  }
}

return this
