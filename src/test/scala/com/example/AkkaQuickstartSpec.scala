//#full-example
package com.example

import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.ai.ConversationManager
import com.example.ai.Models._
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A ConversationManager" must {
    //#test
    "add messages to history" in {
      val underTest = spawn(ConversationManager())
      val probe = createTestProbe[ConversationHistory]()
      
      // Add a message
      underTest ! AddMessage(Message("user", "Hello"))
      
      // Get history
      underTest ! GetHistory(probe.ref)
      val history = probe.receiveMessage()
      
      assert(history.messages.length == 1)
      assert(history.messages.head.role == "user")
      assert(history.messages.head.content == "Hello")
    }
    
    "maintain conversation history" in {
      val underTest = spawn(ConversationManager())
      val probe = createTestProbe[ConversationHistory]()
      
      // Add multiple messages
      underTest ! AddMessage(Message("user", "Hello"))
      underTest ! AddMessage(Message("assistant", "Hi there!"))
      underTest ! AddMessage(Message("user", "How are you?"))
      
      // Get history
      underTest ! GetHistory(probe.ref)
      val history = probe.receiveMessage()
      
      assert(history.messages.length == 3)
      assert(history.messages(0).content == "Hello")
      assert(history.messages(1).content == "Hi there!")
      assert(history.messages(2).content == "How are you?")
    }
    
    "clear history" in {
      val underTest = spawn(ConversationManager())
      val probe = createTestProbe[ConversationHistory]()
      
      // Add messages
      underTest ! AddMessage(Message("user", "Hello"))
      underTest ! AddMessage(Message("assistant", "Hi!"))
      
      // Clear history
      underTest ! ClearHistory()
      
      // Get history
      underTest ! GetHistory(probe.ref)
      val history = probe.receiveMessage()
      
      assert(history.messages.isEmpty)
    }
    //#test
  }

}
//#full-example
