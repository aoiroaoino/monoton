package com.example.controllers

import ocicat.http.Response
import ocicat.server.{Controller, Handler}

class UserController extends Controller {

  def create: Handler[Response] = Handler.TODO

  def list: Handler[Response] =
    for {
      _ <- Handler.pure(println("start get users"))
      _ <- Handler.WIP // 後続処理が評価されていなければ、ここで打ち切ることができる
      name <- Handler.later[String] {
        println("read user from database")
        throw new Exception("no connection")
      }
    } yield Response.Ok(name)
}