package com.example.ai

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.example.ai.Models._

object ConversationManager {
  
  def apply(maxHistorySize: Int = 20): Behavior[ConversationCommand] = {
    active(List.empty, maxHistorySize)
  }
  
  private def active(history: List[Message], maxHistorySize: Int): Behavior[ConversationCommand] = {
    Behaviors.receive { (context, message) =>
      message match {
        case AddMessage(msg) =>
          context.log.debug("Adding message to history: {} - {}", msg.role, msg.content.take(50))
          val newHistory = (history :+ msg).takeRight(maxHistorySize)
          active(newHistory, maxHistorySize)
          
        case GetHistory(replyTo) =>
          context.log.debug("Returning conversation history with {} messages", history.length)
          replyTo ! ConversationHistory(history)
          Behaviors.same
          
        case ClearHistory() =>
          context.log.info("Clearing conversation history")
          active(List.empty, maxHistorySize)
      }
    }
  }
}
