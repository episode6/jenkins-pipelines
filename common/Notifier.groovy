/**
 * Notify a user(/group?) via pushbullet.
 */
def version = '0.0.6'

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

def notifyFailure(String stageName, String outputLog) {
  String message = "Job Failed: ${env.JOB_NAME}\nBuild #${env.BUILD_NUMBER}\nStage: ${stageName}"
  def outputLogs = outputLog.tokenize("\n")
  for (int i = 0; i < outputLogs.size(); i++) {
    String logLine = outputLogs[i]
    if (logLine.contains("FAIL")) {
      message = "${message}\n\n${logLine}"
    }
  }
  notifyPushbullet(message)
}

return this
