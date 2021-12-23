package zhttp.internal

import sttp.client3.asynchttpclient.zio.{SttpClient, send}
import sttp.client3.{UriContext, asWebSocketUnsafe, basicRequest, Response => SResponse}
import sttp.model.{Header => SHeader}
import sttp.ws.WebSocket
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.internal.AppCollection.HttpEnv
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.DefaultRunnableSpec
import zio.{Has, Task, ZIO, ZManaged}

/**
 * Should be used only when e2e tests needs to be written which is typically for logic that is part of the netty based
 * backend. For most of the other use cases directly running the HttpApp should suffice. HttpRunnableSpec spins of an
 * actual Http server and makes requests.
 */
abstract class HttpRunnableSpec extends DefaultRunnableSpec { self =>
  def serve[R <: Has[_]](
    app: HttpApp[R, Throwable],
  ): ZManaged[AppPort with R with EventLoopGroup with ServerChannelFactory, Nothing, Unit] =
    for {
      port <- Server.make(Server.app(app) ++ Server.port(0) ++ Server.paranoidLeakDetection).orDie
      _    <- ZIO.accessM[AppPort](_.get.set(port)).toManaged_
    } yield ()

  def request(
    path: Path = !!,
    method: Method = Method.GET,
    content: String = "",
    headers: Headers = Headers.empty,
  ): ZIO[EventLoopGroup with ChannelFactory with AppPort, Throwable, Client.ClientResponse] = {
    for {
      port <- ZIO.accessM[AppPort](_.get.get)
      data = HttpData.fromText(content)
      response <- Client.request(
        Client.ClientParams(method -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)), headers, data),
        ClientSSLOptions.DefaultSSL,
      )
    } yield response
  }

  def status(path: Path): ZIO[EventLoopGroup with ChannelFactory with AppPort, Throwable, Status] = {
    for {
      port   <- ZIO.accessM[AppPort](_.get.get)
      status <- Client
        .request(
          Method.GET -> URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)),
          ClientSSLOptions.DefaultSSL,
        )
        .map(_.status)
    } yield status
  }

  def webSocketRequest(
    path: Path = !!,
    headers: Headers = Headers.empty,
  ): ZIO[SttpClient with AppPort, Throwable, SResponse[Either[String, WebSocket[Task]]]] = {
    // todo: uri should be created by using URL().asString but currently support for ws Scheme is missing
    for {
      port <- ZIO.accessM[AppPort](_.get.get)
      url                       = s"ws://localhost:$port${path.asString}"
      headerConv: List[SHeader] = headers.toList.map(h => SHeader(h._1.toString(), h._2.toString()))
      res <- send(basicRequest.get(uri"$url").copy(headers = headerConv).response(asWebSocketUnsafe))
    } yield res
  }

  implicit class RunnableHttpAppSyntax(app: HttpApp[HttpEnv, Throwable]) {
    def deploy: ZIO[HttpAppCollection, Nothing, String] = AppCollection.deploy(app)

    def request(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): ZIO[EventLoopGroup with ChannelFactory with AppPort with HttpAppCollection, Throwable, Client.ClientResponse] =
      for {
        id       <- deploy
        response <- self.request(path, method, content, Headers(AppCollection.APP_ID, id) ++ headers)
      } yield response

    def requestBodyAsString(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): ZIO[EventLoopGroup with ChannelFactory with AppPort with HttpAppCollection, Throwable, String] =
      request(path, method, content, headers).flatMap(_.getBodyAsString)

    def requestHeaderValueByName(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    )(
      name: CharSequence,
    ): ZIO[EventLoopGroup with ChannelFactory with AppPort with HttpAppCollection, Throwable, Option[String]] =
      request(path, method, content, headers).map(_.getHeaderValue(name))

    def requestStatus(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): ZIO[EventLoopGroup with ChannelFactory with AppPort with HttpAppCollection, Throwable, Status] =
      request(path, method, content, headers).map(_.status)

    def webSocketStatusCode(
      path: Path = !!,
      headers: Headers = Headers.empty,
    ): ZIO[SttpClient with AppPort with HttpAppCollection, Throwable, Int] = for {
      id  <- deploy
      res <- self.webSocketRequest(path, Headers(AppCollection.APP_ID, id) ++ headers)
    } yield res.code.code
  }
}
