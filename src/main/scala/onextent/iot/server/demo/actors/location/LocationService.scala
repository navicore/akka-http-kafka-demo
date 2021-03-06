package onextent.iot.server.demo.actors.location

import java.util.UUID

import akka.actor._
import akka.cluster.sharding.ShardRegion
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import onextent.iot.server.demo.Conf
import onextent.iot.server.demo.actors.fleet.FleetActor.AddLocationToFleet
import onextent.iot.server.demo.actors.location.LocationActor._
import onextent.iot.server.demo.models.Location

object LocationService extends Conf {

  def props(fleetService: ActorRef)(implicit timeout: Timeout) = Props(new LocationService(fleetService))
  def shardName = "locationService"

  def SHARDS: Int = locationServiceShards

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: GetLocation            => (m.id.toString, m)
    case m: GetLocationAssessments => (m.id.toString, m)
    case m: GetLocationDevices     => (m.id.toString, m)
    case m: CreateLocation         => (m.location.id.toString, m)
    case m: SetLocationAssessment  => (m.locationId.toString, m)
    case m: AddDeviceToLocation    => (m.locationId.toString, m)
  }

  def calcShardName(uuid: UUID): String = {
    s"${Math.abs(uuid.getLeastSignificantBits) % SHARDS}"
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case m: GetLocation            => calcShardName(m.id)
    case m: GetLocationAssessments => calcShardName(m.id)
    case m: GetLocationDevices     => calcShardName(m.id)
    case m: CreateLocation         => calcShardName(m.location.id)
    case m: SetLocationAssessment  => calcShardName(m.locationId)
    case m: AddDeviceToLocation    => calcShardName(m.locationId)
  }
}

class LocationService(fleetService: ActorRef)(implicit timeout: Timeout)
    extends Actor
    with LazyLogging {

  logger.debug(s"actor ${context.self.path} created")

  def create(actorId: String, location: Location): Unit = {
    context.actorOf(LocationActor.props(location), actorId)
    sender() ! location
    location.fleet match {
      case Some(fleetId) =>
        fleetService ! AddLocationToFleet(location, fleetId)
      case _ =>
    }
  }

  override def receive: PartialFunction[Any, Unit] = {

    case GetLocation(id) =>
      def notFound(): Unit = sender() ! None
      context.child(id.toString).fold(notFound())(_ forward GetLocation(id))

    case GetLocationAssessments(id) =>
      def notFound(): Unit = sender() ! None
      context
        .child(id.toString)
        .fold(notFound())(_ forward GetLocationAssessments(id))

    case GetLocationDevices(id) =>
      def notFound(): Unit = sender() ! None
      context
        .child(id.toString)
        .fold(notFound())(_ forward GetLocationDevices(id))

    case CreateLocation(location) =>
      context
        .child(location.id.toString)
        .fold(create(location.id.toString, location))(_ => {
          sender() ! LocationAlreadyExists(location)
        })

    case SetLocationAssessment(assessment, locationId) =>
      def notFound(): Unit =
        logger.warn(
          s"location $locationId not found for assessment $assessment update")
      context.child(locationId.toString).fold(notFound())(_ forward assessment)

    case AddDeviceToLocation(device, _) =>
      device.location match {
        case Some(l) =>
          context
            .child(l.toString)
            .fold(logger.warn(s"location not found: $device"))(
              _ forward AddDeviceToLocation(device, l))
        case _ => logger.warn(s"no location field: $device")
      }

  }

}
