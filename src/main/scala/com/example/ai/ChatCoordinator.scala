package com.example.ai

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import com.example.ai.Models._
import org.apache.pekko.util.Timeout
import scala.concurrent.duration._

object ChatCoordinator {
  
  sealed trait CoordinatorCommand
  case class HandleChatRequest(request: ChatRequest) extends CoordinatorCommand
  private case class HistoryReceived(history: ConversationHistory, userMessage: String, originalReplyTo: ActorRef[ChatResponse]) extends CoordinatorCommand
  private case class LLMResponseReceived(response: LLMResponse, originalReplyTo: ActorRef[ChatResponse]) extends CoordinatorCommand
  
  def apply(apiKey: String): Behavior[CoordinatorCommand] = Behaviors.setup { context =>
    implicit val timeout: Timeout = Timeout(30.seconds)
    
    context.log.info("ChatCoordinator starting with API key: {}...", apiKey.take(10))
    
    val llmClient = context.spawn(LLMClient(apiKey), "llm-client")
    val conversationManager = context.spawn(ConversationManager(), "conversation-manager")
    
    Behaviors.receiveMessage {
      case HandleChatRequest(request) =>
        context.log.info("Handling chat request: {}", request.userMessage)
        
        // Get conversation history
        context.ask(conversationManager, GetHistory.apply) {
          case scala.util.Success(history) => 
            context.log.info("Successfully received history")
            HistoryReceived(history, request.userMessage, request.replyTo)
          case scala.util.Failure(ex) =>
            context.log.error("Failed to get history: {}", ex.getMessage, ex)
            HistoryReceived(ConversationHistory(List.empty), request.userMessage, request.replyTo)
        }
        
        Behaviors.same
        
      case HistoryReceived(history, userMessage, originalReplyTo) =>
        context.log.info("Received history with {} messages", history.messages.length)
        
        // Add user message to history
        val userMsg = Message("user", userMessage)
        conversationManager ! AddMessage(userMsg)
        
        // Prepare messages for LLM (history + new user message)
        val allMessages = history.messages :+ userMsg
        
        context.log.info("Sending {} messages to LLM", allMessages.length)
        
        // Send to LLM
        context.ask(llmClient, (ref: ActorRef[LLMResponse]) => SendToLLM(allMessages, ref)) {
          case scala.util.Success(response) => 
            context.log.info("Successfully received LLM response")
            LLMResponseReceived(response, originalReplyTo)
          case scala.util.Failure(ex) =>
            context.log.error("Failed to get LLM response: {}", ex.getMessage, ex)
            LLMResponseReceived(LLMFailure(s"Request failed: ${ex.getMessage}"), originalReplyTo)
        }
        
        Behaviors.same
        
      case LLMResponseReceived(response, originalReplyTo) =>
        response match {
          case LLMSuccess(content) =>
            context.log.info("Received successful LLM response")
            
            // Add assistant message to history
            val assistantMsg = Message("assistant", content)
            conversationManager ! AddMessage(assistantMsg)
            
            // Send response to original requester
            originalReplyTo ! ChatSuccess(content, List.empty)
            
          case LLMFailure(error) =>
            context.log.error("LLM request failed: {}", error)
            originalReplyTo ! ChatError(error)
        }
        
        Behaviors.same
    }
  }
}
