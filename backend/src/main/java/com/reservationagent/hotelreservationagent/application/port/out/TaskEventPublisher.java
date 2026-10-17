package com.reservationagent.hotelreservationagent.application.port.out;

import com.reservationagent.hotelreservationagent.application.event.TaskProgressEvent;

public interface TaskEventPublisher {

    void publish(TaskProgressEvent event);
}
