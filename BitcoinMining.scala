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

// SHA256 object
object SHA256 {
	def hash(string: String): String = {
      MessageDigest.getInstance("SHA-256").digest(string.getBytes).foldLeft("")((s: String, b: Byte) => s +
        Character.forDigit((b & 0xf0) >> 4, 16) +
        Character.forDigit(b & 0x0f, 16))
    }
}


object BitcoinMining extends App {
	var workSize = 1000
	var threshold = 100000
	var initActors = 10
	if(args.length != 1){
			println("ERROR: INVALID NO. OF ARGUMENTS")
			System.exit(1)
		} else{
			if(args(0).contains(".")){
				var ip = args(0)
				val remoteSystem = ActorSystem("RemoteBitCoinSystem", ConfigFactory.load(ConfigFactory.parseString("""akka {
  actor {
    provider = "akka.remote.RemoteActorRefProvider"
  }
  remote {
    enabled-transports = ["akka.remote.netty.tcp"]
    netty.tcp {
      port = 13000
    }
 }
}""")))
				var worker = remoteSystem.actorOf(Props(new Worker()), name = "Worker")
        worker ! Worker.RemoteWorker
				var master = remoteSystem.actorSelection("akka.tcp://BitCoinSystem@" + ip + ":12000/user/Master")
				master.tell(Master.New_Worker, worker)
			}else {
				var leadingZeros = args(0).toInt
				val system = ActorSystem.create("BitCoinSystem", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 12000 } } } } } """)))
          		val master = system.actorOf(Props(new Master(leadingZeros, workSize, initActors, threshold)), name = "Master")
          		master ! Master.Start 
			}
		}
}