import play.api.GlobalSettings
import play.api.Logger

object Global extends GlobalSettings {
  override def onStart(app: play.api.Application) {
    Logger.info("Trigger " + controllers.Application)
  }
}