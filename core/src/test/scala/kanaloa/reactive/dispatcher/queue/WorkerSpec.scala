package kanaloa.reactive.dispatcher.queue

import akka.actor.{PoisonPill, ActorSystem, ActorRef, ActorRefFactory}
import akka.testkit.{TestActorRef, TestProbe}
import kanaloa.reactive.dispatcher.queue.Queue.RequestWork
import kanaloa.reactive.dispatcher.{Backend, ResultChecker, SpecWithActorSystem}

import scala.concurrent.Future
import scala.concurrent.duration._

case class Result(value: Any)

class TestBackend(delay: FiniteDuration = Duration.Zero)(implicit system: ActorSystem) extends Backend {
  var prob = TestProbe("backend")
  override def apply(f: ActorRefFactory): Future[ActorRef] = {
    import system.dispatcher
    Future {
      if (delay > Duration.Zero) Thread.sleep(delay.toMillis)
      prob.ref
    }
  }
}

class WorkerSpec extends SpecWithActorSystem {
  def newWorker(queueRef: ActorRef, b: Backend): TestActorRef[Worker] = TestActorRef[Worker](Worker.default(queueRef, b)(ResultChecker.simple[Result]))

  trait WorkerScope {
    val queueProb = TestProbe("queue")
    val backend = new TestBackend()
    val worker = newWorker(queueProb.ref, backend)
  }

  "worker" should {

    "retrieve work from queue and send to backend" in new WorkerScope {
      queueProb.expectMsgType[RequestWork]
      queueProb.reply(Work("work"))
      backend.prob.expectMsg("work")
      worker.stop()
    }

    "recover from dead routee while waiting" in new WorkerScope {
      queueProb.expectMsgType[RequestWork]

      val oldBackendRef = backend.prob.ref

      val newBackendProb = TestProbe("newBackend")
      backend.prob = newBackendProb

      oldBackendRef ! PoisonPill

      awaitAssert(worker.underlyingActor.getRoutee should ===(newBackendProb.ref))

      queueProb.reply(Work("w", replyTo = Some(self)))

      newBackendProb.expectMsg("w")

      newBackendProb.reply(Result(1))

      expectMsg(Result(1))

      worker.stop()
    }

    "recover from dead routee while working" in new WorkerScope {

      queueProb.expectMsgType[RequestWork]
      queueProb.reply(Work("w"))

      backend.prob.expectMsg("w")

      val oldBackendRef = backend.prob.ref

      val newBackendProb = TestProbe("newBackend")
      backend.prob = newBackendProb

      oldBackendRef ! PoisonPill

      awaitAssert(worker.underlyingActor.getRoutee should ===(newBackendProb.ref))

      newBackendProb.expectNoMsg()

      worker.stop()
    }

    "accept work during restarting" in {
      val queueProb = TestProbe("queue")
      val backend = new TestBackend(delay = 100.milliseconds)
      val worker = newWorker(queueProb.ref, backend)

      queueProb.expectMsgType[RequestWork]
      queueProb.reply(Work("w"))

      backend.prob.expectMsg("w")
    }

    "shutdown when retiring during starting" in {
      val queueProb = TestProbe("queue")
      val backend = new TestBackend(delay = 5.seconds)
      val worker = newWorker(queueProb.ref, backend)
      watch(worker)
      worker ! Worker.Retire
      expectTerminated(worker)
    }

    "reject work when retiring during starting" in {
      val queueProb = TestProbe("queue")
      val backend = new TestBackend(delay = 5.seconds)
      val worker = newWorker(queueProb.ref, backend)

      worker ! Work("w")
      watch(worker)

      worker ! Worker.Retire

      expectMsgType[Rejected]

      expectTerminated(worker)
    }

  }

}