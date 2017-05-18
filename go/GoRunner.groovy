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
  stage('test') {
    runGoCmds("test", srcDir, goName, ["go test -timeout ${testTimeout} -cover ./..."])
  }
}

def runGoCmds(String stageName, String srcDir, String goName, List<String> goCmds) {
  def goRoot = tool name: goName, type: 'go'
  withEnv(["GOROOT=${goRoot}", "GOPATH=${pwd()}", "PATH+GO=${goRoot}/bin:${pwd()}/bin"]) {
    dir(srcDir) {
      runner.runStagedCommands(stageName, goCmds)
    }
  }
}

return this
