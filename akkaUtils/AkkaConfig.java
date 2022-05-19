package akkaUtils;


import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import akka.actor.ActorSystem;

public class AkkaConfig {
  public static ActorSystem newSystem(String name,int port,Map<String,String> mapPathIp) {
    List<String> ips = new IpLocator().guessMyIp();
    if(ips.size()!=1){
      throw new Error("Unable to detect ip between the following options:\n"+ips);
      }
    Config config = ConfigFactory.parseString(
      "akka.actor.guardian-supervisor-strategy = "+TerminatorSupervisor.class.getCanonicalName()
      ).withFallback(ConfigFactory.parseString(        
      "akka.actor.provider = remote"
      )).withFallback(ConfigFactory.parseString(
      "akka.remote.enabled-transports = [\"akka.remote.netty.tcp\"]"
      )).withFallback(ConfigFactory.parseString(
      "akka.remote.netty.tcp.hostname = \""+ips.get(0)+"\""
      )).withFallback(ConfigFactory.parseString(
      "akka.remote.netty.tcp.port = "+port
      ));
    for(Entry<String, String> e:mapPathIp.entrySet()) {
      Config c = ConfigFactory.parseString(
        "akka.actor.deployment.\"/"+e.getKey()+"\".remote = \"akka.tcp://OpenAkka@"+e.getValue()+":2500\"");
      config=config.withFallback(c);
      }
    config=config.withFallback(ConfigFactory.load());
    return ActorSystem.create(name,config);
    }
  }