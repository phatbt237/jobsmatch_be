package vn.career.survey.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.survey.api.dto.AdminQuestionResponse;
import vn.career.survey.api.dto.QuestionRequest;
import vn.career.survey.application.SurveyAdminService;

@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - questions")
public class AdminQuestionController {

    private final SurveyAdminService service;

    @GetMapping("/{id}")
    public ApiResponse<AdminQuestionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.getQuestion(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminQuestionResponse>> create(@Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createQuestion(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminQuestionResponse> update(@PathVariable UUID id, @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.ok(service.updateQuestion(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.deleteQuestion(id);
        return ApiResponse.ok(null);
    }
}
