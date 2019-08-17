package com.example

import java.util.concurrent.Executors

import com.example.controllers.{HealthCheckController, RiseErrorController}
import ocicat.server.{NettyServer, Server}

import scala.concurrent.ExecutionContext

class ExampleModule {

  private val healthCheckController = new HealthCheckController
  private val riseErrorController   = new RiseErrorController

  private val router = new ExampleRouter(healthCheckController, riseErrorController)

  private val serverPort = 8080

  private val requestExecutor: ExecutionContext = {
    val es = Executors.newFixedThreadPool(32)
    ExecutionContext.fromExecutor(es)
  }

  val server: Server = new NettyServer(serverPort, router.impl, requestExecutor)
}