package util
package security

import models.User
import collins.cache.ConfigCache
import collins.validation.File

import sun.misc.BASE64Encoder
import java.security.MessageDigest
import play.api.Logger
import com.google.common.cache._
import java.util.concurrent.TimeUnit
import collins.permissions.{PermissionsHelper, Privileges}
import org.squeryl.PrimitiveTypeMode._

trait AuthenticationProvider {
  protected val logger = Logger.logger
  type Credentials = Tuple2[String,String]
  protected lazy val cache: LoadingCache[Credentials, Option[User]] = CacheBuilder.newBuilder()
                                .maximumSize(100)
                                .expireAfterWrite(cacheTimeout, TimeUnit.MILLISECONDS)
                                .build(
                                  new CacheLoader[Credentials, Option[User]] {
                                    override def load(creds: Credentials): Option[User] = {
                                      logger.info("Loading user %s from backend".format(creds._1))
                                      authenticate(creds._1, creds._2)
                                    }
                                  }
                                )
  def authType: String
  def authenticate(username: String, password: String): Option[User]
  def useCachedCredentials: Boolean = AuthenticationProviderConfig.cacheCredentials
  def cacheTimeout: Long = AuthenticationProviderConfig.cacheTimeout
  def tryAuthCache(username: String, password: String): Option[User] = {
    if (!useCachedCredentials) {
      authenticate(username, password)
    } else {
      cache.get((username, password)) match {
        case None =>
          cache.invalidate((username, password))
          None
        case Some(u) =>
          Some(u)
      }
    }
  }
}

object AuthenticationProvider {
  val Default = new MockAuthenticationProvider
  def filename = AuthenticationProviderConfig.permissionsFile

  private val logger = Logger("util.security.AuthenticationProvider")

  lazy private val permissionsCache =
    ConfigCache.create(AuthenticationProviderConfig.cachePermissionsTimeout, PermissionsLoader())

  def get(name: String): AuthenticationProvider = {
    new MixedAuthenticationProvider(name)
  }

  def permissions(concern: String): Option[Set[String]] = {
    val p = privileges
    if (p.hasConcern(concern)) {
      val c = p.getConcern(concern)
      logger.trace("Concern '%s' has concerns '%s'".format(
        concern, c.mkString(",")))
      Some(c)
    } else {
      logger.trace("Missing configuration for concern %s".format(concern))
      None
    }
  }

  def userIsAuthorized(user: User, spec: SecuritySpecification): Boolean = {
    val p = privileges
    val concern = spec.securityConcern
    if (concern == SecuritySpec.LegacyMarker) {
      logger.debug("Found legacy security spec, defaulting to basic roles")
      loggedAuth {
        user.rolesSet.map(_.toLowerCase).intersect(spec.requiredCredentials.map(_.toLowerCase)).size > 0
      }
    } else {
      logger.trace("Have concern '%s'".format(concern))
      loggedAuth {
        user.rolesSet
          .find { role =>
            val perm = p.groupHasPermission(role, concern)
            logger.debug("Checking group permission for role %s concern %s was %s".format(
              role, concern, perm.toString))
            perm
          }
          .map(_ => true)
          .getOrElse {
            val perm = p.userHasPermission(user.username, concern)
            logger.debug("Checking user permission for username %s concern %s was %s".format(
              user.username, concern, perm.toString))
            perm
          }
      }
    }
  }

  def getUser(username: String, auth_type: String): Option[User] = {
    transaction {
      val query = User.users.where(u => u.username === username and u.auth_type === auth_type)
      query.isEmpty match {
        case true => None
        case false => Some(query.single)
      }
    }
  }

  // This is consistent with how apache encrypts SHA1 if the salt is blank
  def hashPassword(password: String, salt: String): String = {
    "{SHA}" + new BASE64Encoder().encode(MessageDigest.getInstance("SHA1").digest((password + salt).getBytes()))
  }

  private def loggedAuth(f: => Boolean): Boolean = {
    val r = f
    logger.debug("Result of authentication was %s".format(r.toString))
    r
  }

  protected[util] def privileges: Privileges = {
    val p = permissionsCache.get(filename)
    logger.trace("Privileges - %s".format(p))
    p
  }

}
