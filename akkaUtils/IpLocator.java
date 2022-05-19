package akkaUtils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class IpLocator {
  public String visibleIP(){
    try(var stream=new URL("https://api.ipify.org").openStream();
      var s=new Scanner(stream, "UTF-8")
      ){return s.useDelimiter("\\A").next();}
    catch (IOException e){throw new Error(e);}
    }
  //public List<String> guessMyIp(){return List.of(myIP());}
  public List<String> guessMyIp() {
    List<String> res=new ArrayList<>();
    List<NetworkInterface>is;
    try{is=Collections.list(NetworkInterface.getNetworkInterfaces());}
    catch(SocketException e){throw new Error(e);}
    for(NetworkInterface n:is){add(res,n);}
    return res;
    }
  void add(List<String> res,NetworkInterface n){
    //filters out 127.0.0.1 and inactive interfaces
    try{if(n.isLoopback() || !n.isUp()){return;}}
    catch(SocketException e){return;}//or throw new Error(e);?
    for(InetAddress a: Collections.list(n.getInetAddresses())){add(res,a);}
    }
  void add(List<String> res,InetAddress a){
    if(!(a instanceof Inet4Address)){return;}
    try{if(a.isReachable(1000)){res.add(a.getHostAddress());}}
    catch(IOException e){return;}//or throw new Error(e);?
    }
  }