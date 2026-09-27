package com.chris64233.cc.satellite.web;

import com.chris64233.cc.satellite.service.SchedulingService;
import com.chris64233.cc.satellite.web.dto.ContactResponse;
import com.chris64233.cc.satellite.web.dto.ScheduleContactRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final SchedulingService schedulingService;

    public ContactController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    /** 排程联系任务。幂等重放返回原排程（200），新建返回 201。 */
    @PostMapping
    public ResponseEntity<ContactResponse> schedule(@Valid @RequestBody ScheduleContactRequest request) {
        SchedulingService.ScheduleResult result = schedulingService.schedule(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ContactResponse.from(result.task()));
    }

    @GetMapping("/{id}")
    public ContactResponse details(@PathVariable long id) {
        return ContactResponse.from(schedulingService.contactDetails(id));
    }

    /** 取消未开始的任务，释放天线占用；重复取消幂等。 */
    @DeleteMapping("/{id}")
    public ContactResponse cancel(@PathVariable long id) {
        return ContactResponse.from(schedulingService.cancel(id));
    }
}
