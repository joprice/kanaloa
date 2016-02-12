package kanaloa.reactive.dispatcher.queue

import kanaloa.reactive.dispatcher.SpecWithActorSystem

import java.time.LocalDateTime

class DispatchDurationSpec extends SpecWithActorSystem {

  "dispatch duration" >> {
    "fails when workers are occupied" >> {
      val history = Vector(
        // this event is ignored since queueLength == 0 and waitingWorked != 0
        // not sure why waiting workers are not taken into account
        DispatchHistoryEntry(
          dispatched = 10,
          queueLength = 0,
          waitingWorkers = 10,
          time = LocalDateTime.now.minusMinutes(3)
        ),
        DispatchHistoryEntry(
          dispatched = 10,
          queueLength = 10,
          waitingWorkers = 10,
          time = LocalDateTime.now.minusMinutes(3)
        ),
        DispatchHistoryEntry(
          dispatched = 10,
          queueLength = 10,
          waitingWorkers = 10,
          time = LocalDateTime.now
        )
      )
      DispatchDuration(history) ==== None
    }
  }
}

