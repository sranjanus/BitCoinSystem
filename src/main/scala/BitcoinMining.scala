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
	var workSize = 1000
	var threshold = 100000
	var initActors = 10
  println(args.length);
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