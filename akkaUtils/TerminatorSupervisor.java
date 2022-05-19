package akkaUtils;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ChildRestartStats;
import akka.actor.SupervisorStrategy;
import scala.PartialFunction;
import scala.collection.Iterable;

class TerminatorSupervisor implements akka.actor.SupervisorStrategyConfigurator{
@Override public SupervisorStrategy create() {
  SupervisorStrategy s = SupervisorStrategy.stoppingStrategy();
  return new SupervisorStrategy() {
    @Override public PartialFunction<Throwable, Directive> decider() {
      return s.decider();}
    @Override public void handleChildTerminated(ActorContext context, ActorRef child, Iterable<ActorRef> children) {
      s.handleChildTerminated(context, child, children);}
    @Override public void processFailure(ActorContext context, boolean restart, ActorRef child, Throwable cause,
        ChildRestartStats stats, Iterable<ChildRestartStats> children) {
      s.processFailure(context, restart, child, cause, stats, children);
      context.system().terminate();
    }};
  }  
}