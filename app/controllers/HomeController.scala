package controllers

import de.htwg.se.scrabble.Scrabble
import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.streams.ActorFlow
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.actor._
import akka.io.Tcp.Event
import de.htwg.se.scrabble.controller.controllerComponent.{ ButtonSet, CardsChanged, GameFieldChanged, GridSizeChanged, InvalidEquation }

import scala.swing.Reactor
import scala.swing.event.ButtonClicked

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() (cc: ControllerComponents)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {

  val gamecontroller = Scrabble.controller

  def text = gamecontroller.gameToString

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  def newGrid() = Action { implicit request: Request[AnyContent] =>
    gamecontroller.init
    Ok("200")
  }

  def submit() = Action { implicit request: Request[AnyContent] =>
    gamecontroller.endTurn
    Ok("200")
  }

  def setCard(x: Int, y: Int, count: Int) = Action { implicit request: Request[AnyContent] =>
    gamecontroller.setGrid(x, y, count)
    Ok("200")
  }

  def switchCards(currPlayer: String) = Action { implicit request: Request[AnyContent] =>
    gamecontroller.changeHand(currPlayer)
    Ok("200")
  }

  def undo() = Action { implicit request: Request[AnyContent] =>
    gamecontroller.undo
    Ok("200")
  }

  def redo() = Action { implicit request: Request[AnyContent] =>
    gamecontroller.redo
    Ok("200")
  }

  def resize(size: Int) = Action { implicit request: Request[AnyContent] =>
    gamecontroller.createFixedSizeGameField(size)
    Ok("200")
  }

  def gridToJson = Action { implicit request: Request[AnyContent] =>
    Ok(gamecontroller.memToJson(gamecontroller.createMemento()))
  }

  def socket = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      println("Connect received")
      ScrabbleWebSocketActorFactory.create(out)
    }
  }

  object ScrabbleWebSocketActorFactory {
    def create(out: ActorRef) = {
      Props(new ScrabbleWebSocketActorFactory(out))
    }
  }

  class ScrabbleWebSocketActorFactory(out: ActorRef) extends Actor with Reactor {
    listenTo(gamecontroller)

    def receive = {
      case msg: String =>
        out ! (gamecontroller.memToJson(gamecontroller.createMemento()).toString())
        println("Sent Json to Client" + msg)
    }

    reactions += {

      case event: GameFieldChanged => sendJsonToClient(event)
      case event: CardsChanged => sendJsonToClient(event)
      case event: InvalidEquation => sendJsonToClient(event)
      case event: GridSizeChanged => sendJsonToClient(event)
    }

    def sendJsonToClient(event: scala.swing.event.Event) = {
      println("Received event from Controller")
      out ! (gamecontroller.memToJson(gamecontroller.createMemento(), event).toString())
    }
  }

}
