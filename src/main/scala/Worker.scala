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

/* Worker class cases object*/
// cases:
// 1. FindBitcoins
// 2. Complete
// 3. Stop
// 4. StopAcknowledge
// 5. RemoteWorker
object Worker {
	case class FindBitcoins(startString : String, workSize : Int, leadingZeros : Int)
	case object Complete
	case object Stop
	case object StopAcknowledge
  case object RemoteWorker
}

// worker class
class Worker() extends Actor {
	import Worker._

	var w_prefix = "shashankranjan;"
	var w_type = "local"
	def receive = {
    case RemoteWorker =>
      w_type = "remote"
		case FindBitcoins(startString, workSize, leadingZeros) => FindBitcoinsImpl(startString, workSize, leadingZeros, sender)
		case Stop =>
			sender ! StopAcknowledge
			context.stop(self)
      if(w_type == "remote"){
        context.system.shutdown
      }
		case _ => println("ERROR: WORKER - INVALID MESSAGE RECEIVED")
	}

	def FindBitcoinsImpl(startString : String, workSize : Int, leadingZeros : Int, sender : ActorRef) {
		var w_workString = startString
		var w_workSize = workSize
		while(w_workSize > 0){
			var hex = SHA256.hash(w_prefix + w_workString)
			if(checkZeros(hex, leadingZeros)){
				sender ! Master.BitCoinFound(w_prefix + w_workString, hex)
			}
			w_workString = StringGenerator.nextString(w_workString)
			w_workSize -= 1
		}
		sender ! Complete

	}

	def checkZeros(hexStr: String, leadingZeros: Int) : Boolean = {
		for(i <- 0 to leadingZeros - 1){	
			if(hexStr.charAt(i) != '0'){
				return false
			}
		}
		return true
	}
}



// SHA256 object
object SHA256 {
	def hash(string: String): String = {
      MessageDigest.getInstance("SHA-256").digest(string.getBytes).foldLeft("")((s: String, b: Byte) => s +
        Character.forDigit((b & 0xf0) >> 4, 16) +
        Character.forDigit(b & 0x0f, 16))
    }
}
