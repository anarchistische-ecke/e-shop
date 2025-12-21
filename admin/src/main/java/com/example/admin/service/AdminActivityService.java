package com.example.admin.service;

import com.example.admin.domain.AdminActivity;
import com.example.admin.repository.AdminActivityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminActivityService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 200;

    private final AdminActivityRepository repository;

    public AdminActivityService(AdminActivityRepository repository) {
        this.repository = repository;
    }

    public AdminActivity record(String username, String action) {
        return record(username, action, null);
    }

    public AdminActivity record(String username, String action, String details) {
        AdminActivity activity = new AdminActivity();
        activity.setUsername(username);
        activity.setAction(action);
        activity.setDetails(details);
        return repository.save(activity);
    }

    public Page<AdminActivity> find(Pageable pageable) {
        Pageable safePageable = normalize(pageable);
        return repository.findAll(safePageable);
    }

    private Pageable normalize(Pageable pageable) {
        int page = pageable != null ? Math.max(0, pageable.getPageNumber()) : DEFAULT_PAGE;
        int size = pageable != null ? pageable.getPageSize() : DEFAULT_SIZE;
        size = Math.max(1, Math.min(size, MAX_SIZE));
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, sort);
    }
}
