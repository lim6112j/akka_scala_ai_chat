package com.example.ai

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import org.apache.pekko.stream.Materializer
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import com.example.ai.Models._

object LLMClient {
  
  // OpenAI API request/response models
  case class OpenAIMessage(role: String, content: String)
  case class OpenAIRequest(model: String, messages: List[OpenAIMessage], temperature: Double = 0.7)
  case class OpenAIChoice(message: OpenAIMessage, finish_reason: String, index: Int)
  case class OpenAIResponse(choices: List[OpenAIChoice])
  
  // Circe encoders/decoders
  implicit val messageEncoder: Encoder[OpenAIMessage] = Encoder.instance { msg =>
    Json.obj(
      "role" -> Json.fromString(msg.role),
      "content" -> Json.fromString(msg.content)
    )
  }
  
  implicit val requestEncoder: Encoder[OpenAIRequest] = Encoder.instance { req =>
    Json.obj(
      "model" -> Json.fromString(req.model),
      "messages" -> Json.arr(req.messages.map(_.asJson): _*),
      "temperature" -> Json.fromDoubleOrNull(req.temperature)
    )
  }
  
  implicit val choiceDecoder: Decoder[OpenAIChoice] = Decoder.instance { cursor =>
    for {
      message <- cursor.downField("message").as[OpenAIMessage]
      finishReason <- cursor.downField("finish_reason").as[String]
      index <- cursor.downField("index").as[Int]
    } yield OpenAIChoice(message, finishReason, index)
  }
  
  implicit val openAIMessageDecoder: Decoder[OpenAIMessage] = Decoder.instance { cursor =>
    for {
      role <- cursor.downField("role").as[String]
      content <- cursor.downField("content").as[String]
    } yield OpenAIMessage(role, content)
  }
  
  implicit val responseDecoder: Decoder[OpenAIResponse] = Decoder.instance { cursor =>
    cursor.downField("choices").as[List[OpenAIChoice]].map(OpenAIResponse.apply)
  }
  
  def apply(apiKey: String): Behavior[LLMCommand] = Behaviors.setup { context =>
    implicit val system = context.system
    implicit val ec: ExecutionContext = context.executionContext
    
    context.log.info("LLMClient initialized with API key: {}...", apiKey.take(10))
    
    Behaviors.receiveMessage {
      case SendToLLM(messages, replyTo) =>
        context.log.info("Sending request to OpenAI API with {} messages", messages.length)
        
        val openAIMessages = messages.map(m => OpenAIMessage(m.role, m.content))
        val request = OpenAIRequest(
          model = "gpt-3.5-turbo",
          messages = openAIMessages,
          temperature = 0.7
        )
        
        val requestJson = request.asJson.noSpaces
        context.log.info("Request JSON: {}", requestJson)
        
        val httpRequest = HttpRequest(
          method = HttpMethods.POST,
          uri = "https://api.openai.com/v1/chat/completions",
          headers = List(Authorization(OAuth2BearerToken(apiKey))),
          entity = HttpEntity(ContentTypes.`application/json`, requestJson)
        )
        
        context.log.info("Making HTTP request to OpenAI API...")
        val responseFuture: Future[HttpResponse] = Http().singleRequest(httpRequest)
        
        context.pipeToSelf(responseFuture) {
          case Success(response) => 
            context.log.info("Received HTTP response with status: {}", response.status)
            ProcessResponse(response, replyTo)
          case Failure(ex) => 
            context.log.error("HTTP request failed: {}", ex.getMessage, ex)
            ProcessError(ex, replyTo)
        }
        
        Behaviors.same
        
      case ProcessResponse(response, replyTo) =>
        implicit val ec: ExecutionContext = context.executionContext
        implicit val mat: Materializer = Materializer(context.system)
        
        context.log.info("Processing response with status: {}", response.status)
        
        if (response.status != StatusCodes.OK) {
          context.log.error("OpenAI API returned error status: {}", response.status)
          val errorFuture = response.entity.toStrict(scala.concurrent.duration.Duration(10, "seconds")).map { entity =>
            val errorBody = entity.data.utf8String
            context.log.error("Error response body: {}", errorBody)
            replyTo ! LLMFailure(s"OpenAI API error: ${response.status} - $errorBody")
          }
          
          context.pipeToSelf(errorFuture) {
            case Success(_) => NoOp
            case Failure(ex) => ProcessError(ex, replyTo)
          }
          
          Behaviors.same
        } else {
          val resultFuture = response.entity.toStrict(scala.concurrent.duration.Duration(10, "seconds")).map { entity =>
            val responseBody = entity.data.utf8String
            context.log.info("Response body: {}", responseBody)
            
            decode[OpenAIResponse](responseBody) match {
              case Right(openAIResponse) =>
                if (openAIResponse.choices.nonEmpty) {
                  val content = openAIResponse.choices.head.message.content
                  context.log.info("Received response from OpenAI: {}", content.take(100))
                  replyTo ! LLMSuccess(content)
                } else {
                  context.log.error("No choices in OpenAI response")
                  replyTo ! LLMFailure("No response from LLM")
                }
              case Left(error) =>
                context.log.error("Failed to parse OpenAI response: {}", error.getMessage)
                context.log.error("Response body was: {}", responseBody)
                replyTo ! LLMFailure(s"Failed to parse response: ${error.getMessage}")
            }
          }
          
          context.pipeToSelf(resultFuture) {
            case Success(_) => NoOp
            case Failure(ex) => 
              context.log.error("Failed to process response entity: {}", ex.getMessage, ex)
              ProcessError(ex, replyTo)
          }
          
          Behaviors.same
        }
        
      case ProcessError(error, replyTo) =>
        context.log.error("Error calling OpenAI API: {}", error.getMessage, error)
        replyTo ! LLMFailure(s"API Error: ${error.getMessage}")
        Behaviors.same
        
      case NoOp =>
        Behaviors.same
    }
  }
}
