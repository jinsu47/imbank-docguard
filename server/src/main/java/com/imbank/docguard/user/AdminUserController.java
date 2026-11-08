package com.imbank.docguard.user;

import com.imbank.docguard.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자 계정 관리 API (ARCHITECTURE.md 5장). 경로 규칙상 ADMIN 만 들어옵니다. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserService.UserRow> list() {
        return service.list();
    }

    @PatchMapping("/{id}")
    public AdminUserService.UserRow update(@PathVariable Long id, @Valid @RequestBody AdminUserService.UpdateRequest request) {
        return service.update(CurrentUser.require(), id, request);
    }
}
