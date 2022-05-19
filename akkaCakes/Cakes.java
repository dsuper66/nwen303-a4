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
			.build();}	
}
class Alice extends Producer<Wheat>{
	CompletableFuture<Wheat> make(){return CompletableFuture.supplyAsync(()->new Wheat());}
	/*
	public Receive createReceive() {
		return receiveBuilder()
			.match(Wheat.class,c-> { products.add(c);				
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
			.build();}*/
}
class Bob extends Producer<Sugar>{
	CompletableFuture<Sugar> make(){
		synchronized(Sugar.class) {
			try {Thread.sleep(2);
			} catch (InterruptedException e) {}
		}
		return CompletableFuture.supplyAsync(()-> new Sugar());}
	/*
	public Receive createReceive() {
	    return receiveBuilder()
			.match(Sugar.class,c-> { products.add(c);
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
			.build();}*/
}
class Charles extends Producer<Cake>{
	ActorRef alice;
	List<ActorRef> bobs = new ArrayList<>();
	public Charles(ActorRef alice,List<ActorRef> bobs){this.alice=alice;this.bobs=bobs;}
	CompletableFuture<Cake> make(){
		//ActorSelection alice = context().actorSelection("akka://Cakes/user/Alice");
		//ActorSelection bob = context().actorSelection("akka://Cakes/user/Bob");
		CompletableFuture<?> wheat = Patterns.ask(alice, 
				new GiveOne(),Duration.ofMillis(10_000_000)).toCompletableFuture();
		CompletableFuture<?> sugar = Patterns.ask(bobs.get(0), 
				new GiveOne(),Duration.ofMillis(10_000_000)).toCompletableFuture();
		//When the ingredients are ready, initiate cake making and return the completable future
		return CompletableFuture.allOf(wheat, sugar)
			.thenApplyAsync(v -> new Cake((Sugar) sugar.join(), (Wheat) wheat.join()));
	}
	/*
	public Receive createReceive() {
		return receiveBuilder()
			.match(Cake.class,c-> {
				products.add(c);
				self().tell(new MakeOne(),self());})
			.match(MakeOne.class,c-> {
				if(products.size() >= maxStorage){ //If at or over limit then stop running
					running = false;}
				else { //pipe a future product to self
					Patterns.pipe(make(), context().dispatcher()).to(self());}})
			.match(GiveOne.class,c-> {
			    //When product list empty, pipe a future product to sender, else send the real thing
				if (products.isEmpty()) { Patterns.pipe(make(), context().dispatcher()).to(sender()); }
				else { sender().tell(products.remove(products.size()-1),self()); }
				//When not-running and list not full then set state as running and tell MakeOne to self
				if (products.size() < maxStorage && !running){
					running = true;
					self().tell(new MakeOne(),self());}})				
			.build();}*/
}
class Tim extends AbstractActor{
	int hunger;
	public Tim(int hunger) {this.hunger=hunger;}
	boolean running=true;
	ActorRef originalSender=null;
	void askForCake(){
		ActorSelection charles = context().actorSelection("akka://Cakes/user/Charles");
		CompletableFuture<?> cake = Patterns.ask(charles, new GiveOne(),
				Duration.ofMillis(10_000_000)).toCompletableFuture();
		cake.join();
		hunger-=1;
		if(hunger>0) {
			System.out.println("YUMMY but I'm still hungry "+hunger);
			askForCake();}
		else {
			running = false;
			originalSender.tell(new Gift(), self());}}
	public Receive createReceive() {
		return receiveBuilder()
			.match(GiftRequest.class,()->originalSender==null,gr->{
				originalSender=sender();					
				askForCake();})
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
			//Alice stays local
		));
		ActorRef alice=//makes wheat
			s.actorOf(Props.create(Alice.class,()->new Alice()),"Alice");
		ActorRef bob=//makes sugar
			s.actorOf(Props.create(Bob.class,()->new Bob()),"Bob");
		ActorRef charles=// makes cakes with wheat and sugar
			s.actorOf(Props.create(Charles.class,()
					->new Charles(alice,List.of(bob))),"Charles");
		ActorRef tim=//tim wants to eat cakes
			s.actorOf(Props.create(Tim.class,()->new Tim(hunger)),"Tim");
		CompletableFuture<Object> gift = Patterns.ask(tim,new GiftRequest(), 
				Duration.ofMillis(10_000_000)).toCompletableFuture();
		try{return (Gift)gift.join();}
		finally{			
			alice.tell(PoisonPill.getInstance(),ActorRef.noSender());
			bob.tell(PoisonPill.getInstance(),ActorRef.noSender());
			charles.tell(PoisonPill.getInstance(),ActorRef.noSender());
			tim.tell(PoisonPill.getInstance(),ActorRef.noSender());
			s.terminate();}}
}