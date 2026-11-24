package com.smartdeploy.meeting;

import com.smartdeploy.user.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/meetings")
@PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
public class MeetingController {

    public record AcceptRequest(int slot) {
    }

    private final MeetingService service;

    public MeetingController(MeetingService service) {
        this.service = service;
    }

    @GetMapping
    public List<MeetingService.MeetingResponse> mine(@AuthenticationPrincipal User me) {
        return service.mine(me);
    }

    @GetMapping("/contacts")
    public List<MeetingService.Contact> contacts(@AuthenticationPrincipal User me) {
        return service.contacts(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingService.MeetingResponse propose(@RequestBody MeetingService.ProposeRequest request, @AuthenticationPrincipal User me) {
        return handle(() -> service.propose(request, me));
    }

    @GetMapping("/{id}")
    public MeetingService.MeetingResponse get(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return handle(() -> service.get(id, me));
    }

    @PostMapping("/{id}/accept")
    public MeetingService.MeetingResponse accept(@PathVariable Long id, @RequestBody AcceptRequest body, @AuthenticationPrincipal User me) {
        return handle(() -> service.accept(id, body == null ? 0 : body.slot(), me));
    }

    @PostMapping("/{id}/decline")
    public MeetingService.MeetingResponse decline(@PathVariable Long id, @AuthenticationPrincipal User me) {
        return handle(() -> service.decline(id, me));
    }

    @GetMapping("/{id}/ics")
    public ResponseEntity<byte[]> ics(@PathVariable Long id, @AuthenticationPrincipal User me) {
        String body = handle(() -> service.ics(id, me));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"smartdeploy-visio-" + id + ".ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    private static <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
