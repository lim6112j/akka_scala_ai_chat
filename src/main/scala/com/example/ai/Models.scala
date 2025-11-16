package com.example.ai

import akka.actor.typed.ActorRef

object Models {
  // Message types for the chat system
  
  case class Message(role: String, content: String)
  
  case class ChatRequest(
    userMessage: String,
    replyTo: ActorRef[ChatResponse]
  )
  
  sealed trait ChatResponse
  case class ChatSuccess(response: String, conversationHistory: List[Message]) extends ChatResponse
  case class ChatError(error: String) extends ChatResponse
  
  // Internal messages for LLM client
  sealed trait LLMCommand
  case class SendToLLM(
    messages: List[Message],
    replyTo: ActorRef[LLMResponse]
  ) extends LLMCommand
  private[ai] case class ProcessResponse(response: akka.http.scaladsl.model.HttpResponse, replyTo: ActorRef[LLMResponse]) extends LLMCommand
  private[ai] case class ProcessError(error: Throwable, replyTo: ActorRef[LLMResponse]) extends LLMCommand
  private[ai] case object NoOp extends LLMCommand
  
  sealed trait LLMResponse
  case class LLMSuccess(content: String) extends LLMResponse
  case class LLMFailure(error: String) extends LLMResponse
  
  // Internal messages for conversation manager
  sealed trait ConversationCommand
  case class AddMessage(message: Message) extends ConversationCommand
  case class GetHistory(replyTo: ActorRef[ConversationHistory]) extends ConversationCommand
  case class ClearHistory() extends ConversationCommand
  
  case class ConversationHistory(messages: List[Message])
}
