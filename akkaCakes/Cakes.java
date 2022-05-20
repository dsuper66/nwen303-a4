package akkaCakes;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import akka.actor.*;
import akka.pattern.Patterns;
import akkaUtils.AkkaConfig;
import dataCakes.Cake;
import dataCakes.Gift;
import dataCakes.Product;
import dataCakes.Sugar;
import dataCakes.Wheat;

@SuppressWarnings("serial")
class GiftRequest implements Serializable{}
@SuppressWarnings("serial")
class MakeOne implements Serializable{}
@SuppressWarnings("serial")
class GiveOne implements Serializable{}
@SuppressWarnings("serial")
class HasProduct implements Serializable{}
//--------
abstract class Producer<T> extends AbstractActor{
	List<Product> products =new ArrayList<>();
	boolean running = false;
	int maxStorage = 10;	
	abstract CompletableFuture<T> make();
	public Receive createReceive() {
		return receiveBuilder()
			.match(Product.class, c-> { 
				products.add(c);				
			    self().tell(new MakeOne(),self());})
			.match(MakeOne.class,c-> {
				if(products.size() >= maxStorage){running = false;}
				else {Patterns.pipe(make(), context().dispatcher()).to(self());}})
			.match(GiveOne.class,c-> {
				if (!products.isEmpty()){sender().tell(products.remove(products.size()-1),self());}
				else {Patterns.pipe(make(), context().dispatcher()).to(sender());}
				if (products.size() < maxStorage && !running){						
				    running = true;
					self().tell(new MakeOne(),self());}})
			.match(HasProduct.class,c-> sender().tell(!products.isEmpty(),self()))
			.build();}	
}
class Alice extends Producer<Wheat>{
	CompletableFuture<Wheat> make(){return CompletableFuture.supplyAsync(()->new Wheat());}
}
class Bob extends Producer<Sugar>{
	CompletableFuture<Sugar> make(){
		synchronized(Sugar.class) {
			try {Thread.sleep(200); //ms time taken to make
			} catch (InterruptedException e) {}
		}
		return CompletableFuture.supplyAsync(()-> new Sugar());}
}
class Charles extends Producer<Cake>{
	ActorRef alice;
	List<ActorRef> bobs = new ArrayList<>();
	public Charles(ActorRef alice,List<ActorRef> bobs){this.alice=alice;this.bobs=bobs;}
	CompletableFuture<Cake> make(){
		CompletableFuture<?> wheat = Patterns.ask(alice, 
				new GiveOne(),Duration.ofMillis(10_000_000)).toCompletableFuture();
		ActorRef chosenBob  = bobs.get(0); //relying on having at least one bob
		//If a bob has product then choose to ask them
		for(ActorRef bob:bobs) {
			CompletableFuture<?> hasProduct = 
					Patterns.ask(bob,new HasProduct(),Duration.ofMillis(10_000_000)).toCompletableFuture();
			//If this bob has product then we have our chosen one
			if ((Boolean)hasProduct.join()) {chosenBob = bob; break;}
		}
		//Get the other bobs making product
		for(ActorRef bob:bobs) {if (bob!=chosenBob) bob.tell(new MakeOne(), self());};
		//Get the wheat from the chosenBob
		CompletableFuture<?> sugar = Patterns.ask(chosenBob, 
				new GiveOne(),Duration.ofMillis(10_000_000)).toCompletableFuture();
		//When the ingredients are ready, initiate cake making and return the completable future
		return CompletableFuture.allOf(wheat, sugar)
			.thenApplyAsync(v -> new Cake((Sugar)sugar.join(), (Wheat)wheat.join()));
	}
}
class Tim extends AbstractActor{
	int hunger;
	public Tim(int hunger) {this.hunger=hunger;}
	boolean running=true;
	ActorRef originalSender=null;
	void askForCake(ActorRef charles){
		CompletableFuture<?> cake = Patterns.ask(charles, new GiveOne(),
				Duration.ofMillis(10_000_000)).toCompletableFuture();
		cake.join();
		hunger-=1;
		if(hunger>0) {
			System.out.println("YUMMY but I'm still hungry "+hunger);
			askForCake(charles);}
		else {
			running = false;
			originalSender.tell(new Gift(), self());}}
	public Receive createReceive() {
		return receiveBuilder()
			.match(ActorRef.class,()->originalSender==null,c->{
				originalSender=sender();					
				askForCake(c);})
			.build();}
}
public class Cakes{
	public static void main(String[] args){
		ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
		long startTime = System.nanoTime();
		Gift g=computeGift(1000);
		assert g!=null;
		System.out.println(
			"\n\n-----------------------------\n\n"+
			g+
			"\n\n-----------------------------\n\n"+
			"time taken:" + ((System.nanoTime()-startTime)/1000000000.00) + "\n");}
	public static Gift computeGift(int hunger){		
		ActorSystem s=AkkaConfig.newSystem("Cakes",2501,Map.of(
			//"Tim","172.17.0.2",
			//"Bob","172.17.0.2",
			//"Charles","172.17.0.2"		
			"Tim","192.168.1.22",
			"Bob0","192.168.1.22",
			"Bob1","192.168.1.22",
			"Bob2","192.168.1.22",
			"Bob3","192.168.1.22",			
			"Charles","192.168.1.22"
			//Alice stays local
		));		
		ActorRef alice=//makes wheat
			s.actorOf(Props.create(Alice.class,()->new Alice()),"Alice");
		//ActorRef bob=//makes sugar
		List<ActorRef> bobs = new ArrayList<>();
		int numberOfBobs = 4;
		for(int i=0;i<numberOfBobs;i++) {
			bobs.add(s.actorOf(Props.create(Bob.class,()->new Bob()),"Bob"+i));}
		ActorRef charles=// makes cakes with wheat and sugar
			s.actorOf(Props.create(Charles.class,()->new Charles(alice,bobs)),"Charles");
		ActorRef tim=//tim wants to eat cakes
			s.actorOf(Props.create(Tim.class,()->new Tim(hunger)),"Tim");
		CompletableFuture<Object> gift = Patterns.ask(tim,charles, 
				Duration.ofMillis(10_000_000)).toCompletableFuture();
		try{return (Gift)gift.join();}
		finally{			
			alice.tell(PoisonPill.getInstance(),ActorRef.noSender());
			for(ActorRef bob:bobs) {bob.tell(PoisonPill.getInstance(),ActorRef.noSender());}
			charles.tell(PoisonPill.getInstance(),ActorRef.noSender());
			tim.tell(PoisonPill.getInstance(),ActorRef.noSender());
			s.terminate();}}
}