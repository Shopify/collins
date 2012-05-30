package util

import models._
import models.{LogMessageType, LogFormat, LogSource}

trait TattlerHelper {
  val pString: Option[String] = None
  def message(user: Option[User], msg: String) = {
    val username = user.filter(!_.isEmpty).orElse(AppConfig.getUser()).map(_.username)
      .orElse(pString).orElse(Some("Unknown")).get
    "User %s: %s".format(username, msg)
  }
}
object TattlerHelper extends TattlerHelper

sealed abstract class Tattler(val source: LogSource.LogSource, override val pString: Option[String] = None) extends TattlerHelper {
  def critical(asset: Asset, user: Option[User], msg: String): AssetLog = {
    AssetLog.critical(
      asset, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  def warning(asset: Asset, user: Option[User], msg: String): AssetLog = {
    AssetLog.warning(
      asset, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  def notice(asset: Asset, user: Option[User], msg: String) = {
    AssetLog.notice(
      asset, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  def note(asset: Asset, user: Option[User], msg: String) = {
    AssetLog.note(
      asset, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
  def informational(asset: Asset, user: Option[User], msg: String): AssetLog = {
    AssetLog.informational(
      asset, message(user, msg), LogFormat.PlainText, source
    ).create()
  }
}
object UserTattler extends Tattler(LogSource.User)
object ApiTattler extends Tattler(LogSource.Api)
object InternalTattler extends Tattler(LogSource.Internal, Some("Internal"))
