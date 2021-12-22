package zhttp.internal

import zhttp.http._

trait HttpAppTestExtensions {
  implicit class HttpAppSyntax[R, E](app: HttpApp[R, E]) {
    def getHeader(name: String): Http[R, E, Request, Option[String]] =
      app.map(res => res.getHeaderValue(name))

    def getHeaders: Http[R, E, Request, Headers] =
      app.map(res => res.getHeaders)

    def getHeaderValues: Http[R, E, Request, List[String]] =
      app.map(res => res.getHeaders.toList.map(_._2.toString))

    def getStatus: Http[R, E, Request, Status] =
      app.map(res => res.status)
  }
}
