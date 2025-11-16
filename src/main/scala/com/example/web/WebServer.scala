package com.example.web

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.Timeout
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import com.example.ai.ChatCoordinator
import com.example.ai.Models._

object WebServer {
  
  case class ChatMessageRequest(message: String)
  case class ChatMessageResponse(response: String, success: Boolean, error: Option[String] = None)
  
  def routes(coordinator: ActorRef[ChatCoordinator.CoordinatorCommand])(implicit system: ActorSystem[_]): Route = {
    implicit val timeout: Timeout = Timeout(30.seconds)
    implicit val ec: ExecutionContext = system.executionContext
    
    concat(
      pathSingleSlash {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, htmlPage))
        }
      },
      path("api" / "chat") {
        post {
          entity(as[String]) { body =>
            decode[ChatMessageRequest](body) match {
              case Right(request) =>
                val responseFuture: Future[ChatResponse] = coordinator.ask { replyTo =>
                  ChatCoordinator.HandleChatRequest(ChatRequest(request.message, replyTo))
                }
                
                onSuccess(responseFuture) {
                  case ChatSuccess(content, _) =>
                    complete(HttpEntity(
                      ContentTypes.`application/json`,
                      ChatMessageResponse(content, success = true).asJson.noSpaces
                    ))
                  case ChatError(error) =>
                    complete(HttpEntity(
                      ContentTypes.`application/json`,
                      ChatMessageResponse("", success = false, Some(error)).asJson.noSpaces
                    ))
                }
              case Left(error) =>
                complete(StatusCodes.BadRequest, 
                  ChatMessageResponse("", success = false, Some(s"Invalid JSON: ${error.getMessage}")).asJson.noSpaces)
            }
          }
        }
      },
      path("health") {
        get {
          complete(HttpEntity(ContentTypes.`application/json`, """{"status":"ok"}"""))
        }
      }
    )
  }
  
  val htmlPage: String = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Chat</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
        }
        
        .chat-container {
            width: 90%;
            max-width: 800px;
            height: 90vh;
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        
        .chat-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px;
            text-align: center;
            font-size: 24px;
            font-weight: bold;
        }
        
        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
            background: #f5f5f5;
        }
        
        .message {
            margin-bottom: 15px;
            display: flex;
            animation: fadeIn 0.3s;
        }
        
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }
        
        .message.user {
            justify-content: flex-end;
        }
        
        .message-content {
            max-width: 70%;
            padding: 12px 16px;
            border-radius: 18px;
            word-wrap: break-word;
            white-space: pre-wrap;
        }
        
        .message.user .message-content {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        
        .message.assistant .message-content {
            background: white;
            color: #333;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        }
        
        .message.error .message-content {
            background: #ff4444;
            color: white;
        }
        
        .chat-input-container {
            padding: 20px;
            background: white;
            border-top: 1px solid #e0e0e0;
            display: flex;
            gap: 10px;
        }
        
        #messageInput {
            flex: 1;
            padding: 12px 16px;
            border: 2px solid #e0e0e0;
            border-radius: 25px;
            font-size: 16px;
            outline: none;
            transition: border-color 0.3s;
        }
        
        #messageInput:focus {
            border-color: #667eea;
        }
        
        #sendButton {
            padding: 12px 30px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 25px;
            font-size: 16px;
            font-weight: bold;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        
        #sendButton:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
        }
        
        #sendButton:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }
        
        .typing-indicator {
            display: none;
            padding: 12px 16px;
            background: white;
            border-radius: 18px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
            width: fit-content;
        }
        
        .typing-indicator.active {
            display: block;
        }
        
        .typing-indicator span {
            height: 8px;
            width: 8px;
            background: #667eea;
            border-radius: 50%;
            display: inline-block;
            margin: 0 2px;
            animation: typing 1.4s infinite;
        }
        
        .typing-indicator span:nth-child(2) {
            animation-delay: 0.2s;
        }
        
        .typing-indicator span:nth-child(3) {
            animation-delay: 0.4s;
        }
        
        @keyframes typing {
            0%, 60%, 100% { transform: translateY(0); }
            30% { transform: translateY(-10px); }
        }
    </style>
</head>
<body>
    <div class="chat-container">
        <div class="chat-header">
            🤖 AI Chat Assistant
        </div>
        <div class="chat-messages" id="chatMessages">
            <div class="message assistant">
                <div class="message-content">
                    Hello! I'm your AI assistant. How can I help you today?
                </div>
            </div>
        </div>
        <div class="chat-input-container">
            <input type="text" id="messageInput" placeholder="Type your message..." />
            <button id="sendButton">Send</button>
        </div>
    </div>

    <script>
        const chatMessages = document.getElementById('chatMessages');
        const messageInput = document.getElementById('messageInput');
        const sendButton = document.getElementById('sendButton');
        
        let typingIndicator = null;
        
        function createTypingIndicator() {
            const div = document.createElement('div');
            div.className = 'message assistant';
            div.innerHTML = '<div class="typing-indicator"><span></span><span></span><span></span></div>';
            return div;
        }
        
        function addMessage(content, type) {
            const messageDiv = document.createElement('div');
            messageDiv.className = `message ${type}`;
            messageDiv.innerHTML = `<div class="message-content">${escapeHtml(content)}</div>`;
            chatMessages.appendChild(messageDiv);
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }
        
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
        
        async function sendMessage() {
            const message = messageInput.value.trim();
            if (!message) return;
            
            addMessage(message, 'user');
            messageInput.value = '';
            sendButton.disabled = true;
            
            typingIndicator = createTypingIndicator();
            chatMessages.appendChild(typingIndicator);
            typingIndicator.querySelector('.typing-indicator').classList.add('active');
            chatMessages.scrollTop = chatMessages.scrollHeight;
            
            try {
                const response = await fetch('/api/chat', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ message: message })
                });
                
                const data = await response.json();
                
                if (typingIndicator) {
                    typingIndicator.remove();
                    typingIndicator = null;
                }
                
                if (data.success) {
                    addMessage(data.response, 'assistant');
                } else {
                    addMessage(data.error || 'An error occurred', 'error');
                }
            } catch (error) {
                if (typingIndicator) {
                    typingIndicator.remove();
                    typingIndicator = null;
                }
                addMessage('Failed to send message: ' + error.message, 'error');
            } finally {
                sendButton.disabled = false;
                messageInput.focus();
            }
        }
        
        sendButton.addEventListener('click', sendMessage);
        messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                sendMessage();
            }
        });
        
        messageInput.focus();
    </script>
</body>
</html>"""
}
