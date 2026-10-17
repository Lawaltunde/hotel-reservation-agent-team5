package com.reservationagent.hotelreservationagent.presentation.rest;

import com.reservationagent.hotelreservationagent.application.port.in.OpenCallRecordingUseCase;
import com.reservationagent.hotelreservationagent.application.port.out.CallRecordingSource.CallRecording;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plays a call recording linked from the report email. The token in the path is the only
 * credential; unknown, malformed and expired tokens all get the same 404.
 */
@RestController
public class RecordingController {

    private final OpenCallRecordingUseCase recordings;

    public RecordingController(OpenCallRecordingUseCase recordings) {
        this.recordings = recordings;
    }

    @GetMapping("/api/recordings/{token}")
    public ResponseEntity<byte[]> recording(@PathVariable String token) {
        CallRecording recording = recordings.open(token).orElseThrow(RecordingNotFoundException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(recording.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("call-recording.mp3").build().toString())
                .header("Referrer-Policy", "no-referrer")
                .body(recording.audio());
    }

    static class RecordingNotFoundException extends RuntimeException {
        RecordingNotFoundException() {
            super("Recording not found or link expired");
        }
    }
}
