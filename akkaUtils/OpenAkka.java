package akkaUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import akka.actor.ActorSystem;
import akka.actor.Terminated;
public class OpenAkka{
  public static void main(String[]args) throws InterruptedException {
    ActorSystem s = AkkaConfig.newSystem("OpenAkka",2500,Map.of());
    String ip=""+s.settings().config().getAnyRef("akka.remote.netty.tcp.hostname");
    System.out.println("Chosen IP is\n------------------------------\n    "
        +ip+"\n------------------------------\n");
    keybordClose(s);
  }
  public static void keybordClose(ActorSystem s) throws InterruptedException{
    CompletableFuture<Terminated> cf = s.getWhenTerminated().toCompletableFuture();
    System.out.println(">>> Press ENTER to exit "+s.name()+" <<<");
    try{while(!cf.isDone()) {
      if(System.in.available()>=1) {s.terminate();return;}
      Thread.sleep(200);
      }}
    catch (IOException ioe) {s.terminate();}
    };  
  }
