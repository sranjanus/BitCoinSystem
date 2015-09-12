import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LoggingReceive
import scala.util.control.Breaks
import java.security.MessageDigest
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.ArrayBuffer
import akka.actor.Terminated


/*Master class cases object*/
// cases:
// 1. Start_Mining
// 2. Complete
// 4. Stop
// 5. New_Worker
// 6. BitCoinFound
object Master {
	case class BitCoinFound(string: String, hex: String)
	case object Start
	case object New_Worker
}

// Master class 
class Master(leadingZeros: Int, workSize: Int, noOfActors: Int, threshold: Int) extends Actor {
	import Master._
	var m_minedCoins: Map[String, String] = Map()
    var m_curString = "A"
    var m_actors = new Array[ActorRef](noOfActors)
    var m_noOfStrProcessed = 0
    var m_actorsActive = noOfActors
    var m_workSize = workSize
    var m_leadingZeros = leadingZeros
    var m_threshold = threshold

    def receive = {
    	case Start => startMining
    	case BitCoinFound(string: String, hex: String) => 
    		m_minedCoins += (string -> hex)
    		println(string + "\t" + hex)

    	case Worker.Complete =>
    		if(m_noOfStrProcessed < m_threshold){
    			sendWorkRequest(sender)
    		} else {
    			sender ! Worker.Stop
    		}

    	case Worker.StopAcknowledge =>
    		m_actorsActive -= 1;
    		if(m_actorsActive == 0){
    			context.system.shutdown
    		}

    	case New_Worker =>
    		if(m_noOfStrProcessed < m_threshold){
    			m_actorsActive += 1
    			sendWorkRequest(sender)
    		} else {
    			sender ! Worker.Stop
    		}

    	case _ => println("INVALID MESSAGE RECEIVED")
    }

    def startMining = {
    	for(i <- 0 to m_actorsActive - 1){
    		m_actors(i) = context.actorOf(Props(classOf[Worker]), name = "Worker_" + i)
    		sendWorkRequest(m_actors(i)) 
    	}
    }

    def sendWorkRequest(sender: ActorRef) = {
    	sender ! Worker.FindBitcoins(m_curString, m_workSize, m_leadingZeros)
    	m_curString = StringGenerator.nextNString(m_curString, m_workSize)
    	m_noOfStrProcessed += m_workSize
    }
}

// string generator object
object StringGenerator {
	def nextString(s: String): String = {
      val length = s.length
      var c = s.charAt(length - 1)
      if (c == 'z' || c == 'Z') return if (length > 1) nextString(s.substring(0, length - 1)) + 'A' else "AA"
      s.substring(0, length - 1) + (c.toInt + 1).toChar
    }

    def nextNString(s: String, n: Int): String = {
      var i = 0
      var temp = s
      for (i <- 1 to n) {
        temp = nextString(temp)
      }
      return temp
    }
}