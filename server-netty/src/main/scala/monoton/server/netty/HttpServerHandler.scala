package monoton.server.netty

import java.net.URI

import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import monoton.http.{Request, Response, ResponseBuilders}
import monoton.server.netty.filters.NotImplementedMethodFilter
import monoton.server.{Handler, Router}

import scala.concurrent.ExecutionContext

class HttpServerHandler(
    router: Router,
    httpFlow: HttpFlow,
    executor: ExecutionContext
) extends SimpleChannelInboundHandler[HttpObject] {
  import HttpHeaderNames._, HttpHeaderValues._

  private implicit val ec: ExecutionContext = executor

  // TODO: URI の検証と Route の検索を Filter にし、Handler の実行と HttpResponse のみを行うように
  val httpRequestFlow = NotImplementedMethodFilter |> httpFlow

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case httpReq: HttpRequest =>
        httpRequestFlow
          .runF(httpReq) { req =>
            (for {
              path <- Handler.catchNonFatal(new URI(httpReq.uri).getPath)(
                _ => ResponseBuilders.BadRequest("invalid uri path")
              )
              route <- Handler.someValue(router.findRoute(req.method, path))(
                ResponseBuilders.NotFound(s"not found: $path")
              )
              response <- route.handler()
            } yield response)
              .run(req)
              .recover {
                case e: Throwable =>
                  e.printStackTrace()
                  ResponseBuilders.InternalServerError("Unexpected error occurred")
              }
          }
          .foreach { httpRes =>
            val f: ChannelFuture   = ctx.write(httpRes)
            val keepAlive: Boolean = HttpUtil.isKeepAlive(httpReq)
            if (!keepAlive) {
              f.addListener(ChannelFutureListener.CLOSE)
            }
            if (keepAlive && !httpReq.protocolVersion.isKeepAliveDefault) {
              httpRes.headers.set(CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            } else {
              httpRes.headers.set(CONNECTION, CLOSE)
            }
            ctx.flush()
          }
      case other => // nop
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush()
  }
}
