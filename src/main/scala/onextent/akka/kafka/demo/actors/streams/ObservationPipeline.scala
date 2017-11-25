package onextent.akka.kafka.demo.actors.streams

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import akka.actor.ActorRef
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import onextent.akka.kafka.demo.Conf.{consumerSettings, parallelism, topic, _}
import onextent.akka.kafka.demo.actors.LocationService.SetAssessment
import onextent.akka.kafka.demo.actors.streams.windows._
import onextent.akka.kafka.demo.models.Assessment

object ObservationPipeline extends LazyLogging {

  def apply(deviceService: ActorRef, locationService: ActorRef)(
      implicit timeout: Timeout): Unit = {

    val eventStream = Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .map(ExtractObservations())
      .mapAsync(parallelism) { EnrichWithDevice(deviceService) }
      .mapAsync(parallelism) { CommitKafkaOffset() }
      .mapConcat (FilterDevicesWithLocations())

    // process observations in windows

    val commandStream = eventStream.statefulMapConcat { () =>
      val generator = new CommandGenerator()
      ev =>
        generator.forEvent(ev)
    }

    // ejs todo groupBy for locations
    // ejs todo groupBy for locations
    // ejs todo groupBy for locations
    // ejs todo groupBy for locations
    val windowStreams = commandStream
      .groupBy(64, command => command.w)
      .takeWhile(!_.isInstanceOf[CloseWindow])
      .fold(AggregateEventData((0L, 0L), 0)) {
        case (agg, OpenWindow(window)) => agg.copy(w = window)
        // always filtered out by takeWhile
        case (agg, CloseWindow(_)) => agg
        case (agg, AddToWindow(ev, _)) =>
          agg.copy(eventCount = agg.eventCount + 1, forId = ev._2)
      }
      .map(agg => {
        val from: ZonedDateTime = ZonedDateTime.from(
          Instant.ofEpochMilli(agg.w._1).atOffset(ZoneOffset.UTC))
        val name = s"count ${from.getHour}:${from.getMinute}"
        (Assessment(name, agg.eventCount, from), agg.forId)
      })
      .async

    windowStreams.mergeSubstreams
      .runForeach { ev =>
        logger.debug(s"assessment: $ev")
         locationService ! SetAssessment(ev._1, ev._2)
      }

  }

}