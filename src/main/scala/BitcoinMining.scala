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


object BitcoinMining extends App {
	var workSize = 10000
	var threshold = 1000000
	var initActors = 3
	if(args.length < 1){
			println("ERROR: INVALID NO. OF ARGUMENTS")
			System.exit(1)
		} else{
      val ValidNumber = "([0-9]*)".r
      args(0) match {
        case s: String if s.contains(".") => 
          var ip = s
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

        case ValidNumber(s) =>
          var leadingZeros = s.toInt
          if(args.length == 4){
            initActors = args(1).toInt
            workSize = args(2).toInt
            threshold = args(3).toInt
          }
          val system = ActorSystem.create("BitCoinSystem", ConfigFactory.load(ConfigFactory.parseString("""{ "akka" : { "actor" : { "provider" : "akka.remote.RemoteActorRefProvider" }, "remote" : { "enabled-transports" : [ "akka.remote.netty.tcp" ], "netty" : { "tcp" : { "port" : 12000 } } } } } """)))
          val master = system.actorOf(Props(new Master(leadingZeros, workSize, initActors, threshold)), name = "Master")
          master ! Master.Start 

        case _ => println("ERROR: INVALID ARGUMENT");
      }
			
		}
}