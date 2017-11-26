package onextent.akka.kafka.demo.actors.streams.functions

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import onextent.akka.kafka.demo.Conf
import onextent.akka.kafka.demo.actors.LocationService.SetAssessment
import onextent.akka.kafka.demo.models.Assessment
import onextent.akka.kafka.demo.models.functions.JsonSupport

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object UpdateLocationActor extends LazyLogging with Conf with JsonSupport {

  def apply(locationService: ActorRef)(implicit timeout: Timeout,
                                       ec: ExecutionContext)
    : ((Assessment, UUID)) => Future[(Assessment, UUID)] = {

    (x: (Assessment, UUID)) =>
      {
        val assessment = x._1
        val location = x._2

        val promise = Promise[(Assessment, UUID)]()

        val f = locationService ask SetAssessment(assessment, location)

        f.onComplete {
          case Success(_) => promise.success(x)
          case Failure(e) => promise.failure(e)
        }
        promise.future
      }
  }

}
