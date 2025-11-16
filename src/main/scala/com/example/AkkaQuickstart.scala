package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.example.ai.ChatCoordinator
import com.example.ai.Models._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import akka.actor.typed.scaladsl.AskPattern._

object AkkaQuickstart extends App {
  
  // Get API key from environment variable
  val apiKey = sys.env.getOrElse("OPENAI_API_KEY", {
    println("ERROR: OPENAI_API_KEY environment variable not set")
    println("Please set it with: export OPENAI_API_KEY=your-api-key")
    sys.exit(1)
  })
  
  println("=== AI Chat Application ===")
  println("Type your messages and press Enter. Type 'quit' to exit.\n")
  
  val system = ActorSystem(Behaviors.setup[ChatCoordinator.CoordinatorCommand] { context =>
    val coordinator = context.spawn(ChatCoordinator(apiKey), "chat-coordinator")
    
    Behaviors.receiveMessage { msg =>
      coordinator ! msg
      Behaviors.same
    }
  }, "ai-chat-system")
  
  implicit val ec: scala.concurrent.ExecutionContext = system.executionContext
  
  // Interactive chat loop
  var continue = true
  while (continue) {
    print("You: ")
    System.out.flush()
    
    val input = try {
      StdIn.readLine()
    } catch {
      case _: Exception => null
    }
    
    if (input == null || input.trim.toLowerCase == "quit") {
      continue = false
    } else if (input.trim.nonEmpty) {
      // Send message and wait for response
      val responseFuture = system.ask[ChatResponse] { replyTo =>
        ChatCoordinator.HandleChatRequest(ChatRequest(input.trim, replyTo))
      }(timeout = 30.seconds, scheduler = system.scheduler)
      
      try {
        val response = Await.result(responseFuture, 30.seconds)
        response match {
          case ChatSuccess(content, _) =>
            println(s"\nAssistant: $content\n")
          case ChatError(error) =>
            println(s"\nError: $error\n")
        }
      } catch {
        case ex: Exception =>
          println(s"\nError: ${ex.getMessage}\n")
      }
    }
  }
  
  println("\nShutting down...")
  system.terminate()
  try {
    Await.result(system.whenTerminated, 5.seconds)
  } catch {
    case _: Exception => // Ignore timeout on shutdown
  }
  println("Goodbye!")
}
