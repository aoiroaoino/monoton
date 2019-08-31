package monoton.server

import java.util.concurrent.atomic.AtomicInteger

import monoton.http.{Method, Request, Response}
import monoton.util.Read

import scala.collection.mutable.ListBuffer
import scala.util.chaining._

trait RoutingDSL extends Router {

  override lazy val routings: Seq[Routing] = builders.map(_.build()).toSeq

  private val builders = ListBuffer.empty[RoutingBuilder]

  def GET: RoutingBuilder =
    RoutingBuilder(id = RoutingBuilder.idGen.incrementAndGet(), method = Some(Method.GET)).tap(upsert)

  def POST: RoutingBuilder =
    RoutingBuilder(id = RoutingBuilder.idGen.incrementAndGet(), method = Some(Method.POST)).tap(upsert)

  def PUT: RoutingBuilder =
    RoutingBuilder(id = RoutingBuilder.idGen.incrementAndGet(), method = Some(Method.PUT)).tap(upsert)

  def TODO: Handler[Response] = Handler.TODO
  def WIP: Handler[Response]  = Handler.WIP

  private[RoutingDSL] def upsert(routeBuilder: RoutingBuilder): Unit = {
    val idx = builders.indexWhere(_.id == routeBuilder.id)
    if (idx == -1) builders += routeBuilder else builders.update(idx, routeBuilder)
  }

  final case class RoutingBuilder(
      id: Int,
      method: Option[Method] = None,
      path: Option[String] = None,
      handlerFactory: Option[String => Handler[Response]] = None
  ) {

    def ~(s: String): RoutingBuilder = copy(path = Some(s)).tap(upsert)

    def to(h: => Handler[Response]): RoutingBuilder = copy(handlerFactory = Some(_ => h)).tap(upsert)
    def to(h: Response): RoutingBuilder             = to(Handler.later(h))

    def to[A0: Read](h: A0 => Handler[Response]): RoutingBuilder = {
      val ps = route.patternSegments
      require(ps.length == 1)
      val factory = { path: String =>
        h(Read[A0].read(route.getPathParams(path)(ps(0))))
      }
      copy(handlerFactory = Some(factory)).tap(upsert)
    }
    def to[A0: Read, A1: Read](h: (A0, A1) => Handler[Response]): RoutingBuilder = {
      val ps = route.patternSegments
      require(ps.length == 2)
      val factory = { path: String =>
        h(
          Read[A0].read(route.getPathParams(path)(ps(0))),
          Read[A1].read(route.getPathParams(path)(ps(1)))
        )
      }
      copy(handlerFactory = Some(factory)).tap(upsert)
    }
    def to[A0: Read, A1: Read, A2: Read](h: (A0, A1, A2) => Handler[Response]): RoutingBuilder = {
      val ps = route.patternSegments
      require(ps.length == 3)
      val factory = { path: String =>
        h(
          Read[A0].read(route.getPathParams(path)(ps(0))),
          Read[A1].read(route.getPathParams(path)(ps(1))),
          Read[A2].read(route.getPathParams(path)(ps(2)))
        )
      }
      copy(handlerFactory = Some(factory)).tap(upsert)
    }

    // internal

    private def route: Route = {
      require(method.isDefined && path.isDefined)
      new Route(method.get, path.get)
    }

    def build(): Routing = {
      require(handlerFactory.isDefined)
      Routing(route, handlerFactory.get)
    }
  }

  object RoutingBuilder {
    private[RoutingDSL] val idGen: AtomicInteger = new AtomicInteger(0)
  }
}