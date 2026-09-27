package com.chris64233.cc.satellite.web;

import com.chris64233.cc.satellite.service.SchedulingService;
import com.chris64233.cc.satellite.web.dto.ContactResponse;
import com.chris64233.cc.satellite.web.dto.ScheduleContactRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final SchedulingService schedulingService;

    public ContactController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse schedule(@Valid @RequestBody ScheduleContactRequest request) {
        return schedulingService.schedule(request);
    }

    @GetMapping("/{id}")
    public ContactResponse get(@PathVariable long id) {
        return schedulingService.getContact(id);
    }

    @PostMapping("/{id}/cancel")
    public ContactResponse cancel(@PathVariable long id) {
        return schedulingService.cancel(id);
    }
}
