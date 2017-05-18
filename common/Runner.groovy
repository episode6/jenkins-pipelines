/**
 * Run a "staged" command/commands, i.e. the main commands to build
 * and test your project.
 */
def version = '0.0.6'
notifier = load("common/Notifier.groovy")

def runStagedCommand(String stageName, String command) {
  runStagedCommands(stageName, [command])
}

def runStagedCommands(String stageName, List<String> commands) {
  Exception err
  String outputLogFilename = getLogFileForStage(stageName)
  try {
    sh "echo \"\" > ${outputLogFilename}"
    for (String cmd : commands) {
      sh "${cmd} >> ${outputLogFilename}"
    }
  } catch (Exception e) {
    err = e
    currentBuild.result = "FAILURE"
  } finally {
    def outputLog = readFile(outputLogFilename)
    echo outputLog
    if (err) {
      notifier.notifyFailure(stageName, outputLog)
      throw err
    }
    return outputLog
  }
}

def getLogFileForStage(String stageName) {
  return "${stageName}-output-log"
}

return this
