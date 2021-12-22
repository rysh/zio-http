package zhttp.http

import zio.test.Assertion._
import zio.test.{assert, _}

object ResponseHelpersSpec {
  val redirectSpec = {
    val location = "www.google.com"
    suite("redirectSpec")(
      test("Temporary redirect should produce a response with a TEMPORARY_REDIRECT") {
        val x = Response.redirect(location)
        assert(x.status)(equalTo(Status.TEMPORARY_REDIRECT)) && assert(x.getHeadersAsList)(
          contains(Headers.location(location)),
        )
      } +
        test("Temporary redirect should produce a response with a location") {
          val x = Response.redirect(location)
          assert(x.getHeadersAsList)(contains(Headers.location(location)))
        } +
        test("Permanent redirect should produce a response with a PERMANENT_REDIRECT") {
          val x = Response.redirect(location)
          assert(x.status)(equalTo(Status.PERMANENT_REDIRECT))
        } +
        test("Permanent redirect should produce a response with a location") {
          val x = Response.redirect(location)
          assert(x.getHeadersAsList)(contains(Headers.location(location)))
        },
    )
  }

  def spec =
    suite("ResponseHelpers")(redirectSpec)
}
