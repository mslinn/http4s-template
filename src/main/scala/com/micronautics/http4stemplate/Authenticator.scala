package com.micronautics.http4s

import cats._, cats.implicits._, cats.data._
import fs2.interop.cats._
import fs2.Task
import java.time._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.server._
import org.http4s.util.string._
import org.reactormonk.{CryptoBits, PrivateKey}

object Authenticator {
  val authUser: Service[Request, Either[String,User]] = Kleisli({ request =>
    val message = for {
      header <- request.headers.get(Authorization).toRight("Couldn't find an Authorization header")
      token <- crypto.validateSignedToken(header.value).toRight("Cookie invalid")
      message <- Either.catchOnly[NumberFormatException](token.toLong).leftMap(_.toString)
    } yield message
    message.traverse(retrieveUser.run)
  })

  val onFailure: AuthedRequest[String] = Kleisli(req => Forbidden(req.authInfo))

  val middleware: AuthMiddleware[User] = AuthMiddleware(authUser, onFailure)

  val authedService: AuthedService[User] =
    AuthedService {
      case GET -> Root / "welcome" as user => Ok(s"Welcome, ${user.name}")
    }

  val middleware: AuthMiddleware[Either[String, User]] = AuthMiddleware(authUser)

  val service: HttpService = middleware(authedService)

  val key: PrivateKey = PrivateKey(scala.io.Codec.toUTF8(scala.util.Random.alphanumeric.take(20).mkString("")))

  val crypto: CryptoBits = CryptoBits(key)

  val clock: Clock = Clock.systemUTC

  def verifyLogin(request: Request): Task[Either[String,User]] = ??? // gotta figure out how to do the form

  val logIn: Service[Request, Response] = Kleisli({ request =>
    verifyLogin(request: Request).flatMap {
      case Left(error) =>
        Forbidden(error)

      case Right(user) =>
        val message = crypto.signToken(user.id.toString, clock.millis.toString)
        Ok("Logged in!").addCookie(Cookie("authcookie", message))
    }
  })

  def retrieveUser: Service[Long, User] = Kleisli(id => Task.delay(???))
}

case class User(id: Long, name: String)
