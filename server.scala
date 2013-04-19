import akka.actor._
import akka.actor.IO.ReadHandle
import java.net.InetSocketAddress
import akka.util.ByteString
import scala.util.matching.Regex

class TCPServer(port: Int) extends Actor {

    val msgReceivedActor = ActorSystem().actorOf(Props[MsgReceivedActor], name = "msgReceivedActor")

    override def preStart {
        IOManager(context.system) listen new InetSocketAddress(port)
    }

    val SetPattern = "^(SET) ([A-Za-z][A-Za-z0-9]+) (.*)$".r
    def parseSetPattern (text:String):Option[(String,String,String)] = {
        for(SetPattern(first, second, third) <- SetPattern.findFirstIn(text)) yield (first, second, third)
    }
    val GetPattern = "^(GET) ([A-Za-z][A-Za-z0-9]+)$".r
    def parseGetPattern (text:String):Option[(String,String)] = {
        for(GetPattern(first, second) <- GetPattern.findFirstIn(text)) yield (first, second)
    }
def receive = {
    case IO.NewClient(server) =>
        server.accept()
    case IO.Read(rHandle: ReadHandle, bytes: ByteString) =>
        msgReceivedActor ! (parseSetPattern(bytes.utf8String), rHandle)
        msgReceivedActor ! (parseGetPattern(bytes.utf8String), rHandle)
        //rHandle.asSocket write bytes.compact
    }
}

class MsgReceivedActor() extends Actor {
    val getOpActor = ActorSystem().actorOf(Props[GetOpActor], name = "getOpActor")
    val setOpActor = ActorSystem().actorOf(Props[SetOpActor], name = "setOpActor")
    def receive = {
        case "hello\n" => println("hello geldi")
        case Seq(a,b) => println(a,b)
        case (Some((command:String, varName:String, content:String)), rHandle:ReadHandle) => if (command == "SET") setOpActor ! (varName, content, rHandle:ReadHandle)
        case (Some((command:String, varName:String)), rHandle:ReadHandle) => if (command == "GET")getOpActor ! (varName, rHandle:ReadHandle)
        case any => println("not matched" + any)
    }
}

class GetOpActor() extends Actor {
    def receive = {
        case (varName:String, rHandle:ReadHandle) => rHandle.asSocket write ByteString(getOperation(varName))
    }
def getOperation(varName:String):String = {
        println("getting" + varName)
        println(StoreMap.getVar(varName))
        StoreMap.getVar(varName)
    }
}

class SetOpActor() extends Actor {
    def receive = {
    case (varName:String, content:String, rHandle:ReadHandle) => rHandle.asSocket write ByteString(setOperation(varName, content))
    }
    def setOperation(varName:String, content:String):String = {
        println("setting" + varName)
        StoreMap.putVar(varName, content)
        "OK"
    }
}

object StoreMap extends scala.collection.mutable.HashMap[String, String] {
    def putVar(varName:String, content:String) = {
        this += (varName -> content)
    }
    def getVar(varName:String):(String) = {
        this(varName)
    }
}

object TCPServer extends App
{
    val port = Option(System.getenv("PORT")) map (_.toInt) getOrElse 8080
    val msgReceivedActor = ActorSystem().actorOf(Props(new MsgReceivedActor()), "msgReceivedActor")
    ActorSystem().actorOf(Props(new TCPServer(port)))
}
