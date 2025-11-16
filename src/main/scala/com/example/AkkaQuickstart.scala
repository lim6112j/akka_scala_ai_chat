package com.example

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import com.example.ai.ChatCoordinator
import com.example.web.WebServer
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn

object AkkaQuickstart extends App {
  
  val apiKey = sys.env.getOrElse("OPENAI_API_KEY", {
    println("ERROR: OPENAI_API_KEY environment variable not set")
    println("Please set it with: export OPENAI_API_KEY=your-api-key")
    sys.exit(1)
  })
  
  implicit val system: ActorSystem[ChatCoordinator.CoordinatorCommand] = 
    ActorSystem(ChatCoordinator(apiKey), "ai-chat-system")
  implicit val executionContext: ExecutionContextExecutor = system.executionContext
  
  val routes = WebServer.routes(system)
  
  val bindingFuture: Future[Http.ServerBinding] = 
    Http().newServerAt("localhost", 8080).bind(routes)
  
  bindingFuture.foreach { binding =>
    println(s"=== AI Chat Web Server ===")
    println(s"Server online at http://localhost:8080/")
    println(s"Open your browser and navigate to: http://localhost:8080/")
    println(s"Press RETURN to stop...")
  }
  
  StdIn.readLine()
  
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
