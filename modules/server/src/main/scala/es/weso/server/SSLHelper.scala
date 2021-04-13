package es.weso.server

import cats.data.NonEmptyList
import cats.effect.{IO, Sync}
import cats.implicits._
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Host, Location}
import org.http4s.server.SSLKeyStoreSupport.StoreInfo
import org.typelevel.ci._

import java.io.{FileInputStream, IOException}
import java.nio.file.Paths
import java.security.{KeyStore, SecureRandom, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object SSLHelper {
  val keyStorePassword: String   = sys.env.getOrElse("KEYSTORE_PASSWORD", "")
  val keyManagerPassword: String = sys.env.getOrElse("KEYMANAGER_PASSWORD", "")
  val keyStorePath: String =
    Paths.get(sys.env.getOrElse("KEYSTORE_PATH", "")).toAbsolutePath.toString
  val storeInfo: StoreInfo = StoreInfo(keyStorePath, keyStorePassword)

  def getContext: SSLContext = {
    if(
      keyStorePassword == "" || keyManagerPassword == "" || keyStorePath == ""
    ) {
      SSLContext.getDefault
    } else {
      try {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)

        val in = new FileInputStream(keyStorePath)
        keyStore.load(in, keyStorePassword.toCharArray)

        val keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray)

        val trustManagerFactory = TrustManagerFactory.getInstance(
          TrustManagerFactory.getDefaultAlgorithm
        )
        trustManagerFactory.init(keyStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
          keyManagerFactory.getKeyManagers,
          trustManagerFactory.getTrustManagers,
          new SecureRandom()
        )
        sslContext
      } catch {
        case io: IOException  => SSLContext.getDefault
        case sec: IOException => SSLContext.getDefault
      }
    }
  }

  def handler(e: Throwable): IO[SSLContext] =
    for {
      _ <- IO(println(s"Exception: $e"))
    } yield SSLContext.getDefault

  def loadContextFromClasspath[F[_]](
      keystorePassword: String,
      keyManagerPass: String
  )(implicit
      F: Sync[F]
  ): F[SSLContext] =
    F.delay {
      val ksStream = this.getClass.getResourceAsStream("/server.jks")
      val ks       = KeyStore.getInstance("JKS")
      ks.load(ksStream, keystorePassword.toCharArray)

      println(s"KeyStore loaded: $ks")

      ksStream.close()

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
          .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
      )

      println(s"kmf before init: $kmf")

      kmf.init(ks, keyManagerPass.toCharArray)

      println(s"kmf after init: $kmf")

      val context = SSLContext.getInstance("TLS")

      println(s"context before init: $context")

      context.init(kmf.getKeyManagers, null, null)

      println(s"context after init: $context")

      context
    }

  def redirectApp(securePort: Int): HttpApp[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._

    HttpApp[IO] { request =>
      {
        val hostCIString: CIString = Host.headerInstance.name
        val rs: Option[NonEmptyList[Header.Raw]] =
          request.headers.get(hostCIString)
        rs match {
          case Some(ls) => {
            ls.filter(_.name == hostCIString).headOption match {
              case Some(header) => {
                val host = header.value
                val baseUri = request.uri.copy(
                  scheme = Scheme.https.some,
                  authority = Some(
                    Authority(
                      userInfo = request.uri.authority.flatMap(_.userInfo),
                      host = RegName(host),
                      port = securePort.some
                    )
                  )
                )
                MovedPermanently(Location(baseUri.withPath(request.uri.path)))
              }
              case None => BadRequest()
            }
          }
          case _ => BadRequest()
        }
      }
    }
  }
}
