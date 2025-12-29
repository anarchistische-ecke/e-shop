package com.example.api.admin;

import com.example.admin.domain.AdminActivity;
import com.example.admin.service.AdminActivityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/admin/activity")
public class ActivityController {

    private final AdminActivityService adminActivityService;

    public ActivityController(AdminActivityService adminActivityService) {
        this.adminActivityService = adminActivityService;
    }

    @GetMapping
    public Page<ActivityResponse> getActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return adminActivityService.find(pageable).map(ActivityResponse::from);
    }

    public record ActivityResponse(UUID id, String user, String action, String details, OffsetDateTime timestamp) {
        public static ActivityResponse from(AdminActivity activity) {
            return new ActivityResponse(
                    activity.getId(),
                    activity.getUsername(),
                    activity.getAction(),
                    activity.getDetails(),
                    activity.getCreatedAt()
            );
        }
    }
}
