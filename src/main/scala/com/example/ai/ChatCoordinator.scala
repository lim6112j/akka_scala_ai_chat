package com.example.ai

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.ai.Models._
import akka.util.Timeout
import scala.concurrent.duration._

object ChatCoordinator {
  
  sealed trait CoordinatorCommand
  case class HandleChatRequest(request: ChatRequest) extends CoordinatorCommand
  private case class HistoryReceived(history: ConversationHistory, userMessage: String, originalReplyTo: ActorRef[ChatResponse]) extends CoordinatorCommand
  private case class LLMResponseReceived(response: LLMResponse, originalReplyTo: ActorRef[ChatResponse]) extends CoordinatorCommand
  
  def apply(apiKey: String): Behavior[CoordinatorCommand] = Behaviors.setup { context =>
    implicit val timeout: Timeout = Timeout(30.seconds)
    
    val llmClient = context.spawn(LLMClient(apiKey), "llm-client")
    val conversationManager = context.spawn(ConversationManager(), "conversation-manager")
    
    Behaviors.receiveMessage {
      case HandleChatRequest(request) =>
        context.log.info("Handling chat request: {}", request.userMessage)
        
        // Get conversation history
        context.ask(conversationManager, GetHistory.apply) {
          case scala.util.Success(history) => HistoryReceived(history, request.userMessage, request.replyTo)
          case scala.util.Failure(ex) =>
            context.log.error("Failed to get history", ex)
            HistoryReceived(ConversationHistory(List.empty), request.userMessage, request.replyTo)
        }
        
        Behaviors.same
        
      case HistoryReceived(history, userMessage, originalReplyTo) =>
        context.log.debug("Received history with {} messages", history.messages.length)
        
        // Add user message to history
        val userMsg = Message("user", userMessage)
        conversationManager ! AddMessage(userMsg)
        
        // Prepare messages for LLM (history + new user message)
        val allMessages = history.messages :+ userMsg
        
        // Send to LLM
        context.ask(llmClient, (ref: ActorRef[LLMResponse]) => SendToLLM(allMessages, ref)) {
          case scala.util.Success(response) => LLMResponseReceived(response, originalReplyTo)
          case scala.util.Failure(ex) =>
            context.log.error("Failed to get LLM response", ex)
            LLMResponseReceived(LLMFailure(ex.getMessage), originalReplyTo)
        }
        
        Behaviors.same
        
      case LLMResponseReceived(response, originalReplyTo) =>
        response match {
          case LLMSuccess(content) =>
            context.log.info("Received successful LLM response")
            
            // Add assistant message to history
            val assistantMsg = Message("assistant", content)
            conversationManager ! AddMessage(assistantMsg)
            
            // Get updated history and send response
            context.ask(conversationManager, GetHistory.apply) {
              case scala.util.Success(history) =>
                originalReplyTo ! ChatSuccess(content, history.messages)
                HandleChatRequest(ChatRequest("", context.system.ignoreRef)) // dummy message
              case scala.util.Failure(_) =>
                originalReplyTo ! ChatSuccess(content, List.empty)
                HandleChatRequest(ChatRequest("", context.system.ignoreRef)) // dummy message
            }
            
          case LLMFailure(error) =>
            context.log.error("LLM request failed: {}", error)
            originalReplyTo ! ChatError(error)
        }
        
        Behaviors.same
    }
  }
}
