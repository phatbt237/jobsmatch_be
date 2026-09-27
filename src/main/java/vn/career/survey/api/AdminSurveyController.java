package vn.career.survey.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.career.common.api.ApiResponse;
import vn.career.common.api.PageRequests;
import vn.career.common.api.PageResponse;
import vn.career.survey.api.dto.AdminSectionResponse;
import vn.career.survey.api.dto.AdminSurveyResponse;
import vn.career.survey.api.dto.AdminSurveySummary;
import vn.career.survey.api.dto.DimensionResponse;
import vn.career.survey.api.dto.SectionRequest;
import vn.career.survey.api.dto.SurveyTitleRequest;
import vn.career.survey.application.SurveyAdminService;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - surveys")
public class AdminSurveyController {

    private final SurveyAdminService service;

    @GetMapping("/dimensions")
    public ApiResponse<List<DimensionResponse>> dimensions() {
        return ApiResponse.ok(service.listDimensions());
    }

    @GetMapping("/surveys")
    public ApiResponse<PageResponse<AdminSurveySummary>> list(@RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/surveys")
    public ResponseEntity<ApiResponse<AdminSurveyResponse>> create(@Valid @RequestBody SurveyTitleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request.title())));
    }

    @GetMapping("/surveys/{id}")
    public ApiResponse<AdminSurveyResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/surveys/{id}")
    public ApiResponse<AdminSurveyResponse> rename(@PathVariable UUID id, @Valid @RequestBody SurveyTitleRequest request) {
        return ApiResponse.ok(service.rename(id, request.title()));
    }

    @DeleteMapping("/surveys/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    /** Copies a survey (usually the published one) into a new DRAFT with the next version number. */
    @PostMapping("/surveys/{id}/new-version")
    public ResponseEntity<ApiResponse<AdminSurveyResponse>> newVersion(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createNewVersion(id)));
    }

    /** Publishes a DRAFT and archives the previously published survey. */
    @PostMapping("/surveys/{id}/publish")
    public ApiResponse<AdminSurveyResponse> publish(@PathVariable UUID id) {
        return ApiResponse.ok(service.publish(id));
    }

    @PostMapping("/surveys/{id}/sections")
    public ResponseEntity<ApiResponse<AdminSectionResponse>> addSection(@PathVariable UUID id,
                                                                        @Valid @RequestBody SectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.addSection(id, request)));
    }

    @PutMapping("/sections/{id}")
    public ApiResponse<AdminSectionResponse> updateSection(@PathVariable UUID id, @Valid @RequestBody SectionRequest request) {
        return ApiResponse.ok(service.updateSection(id, request));
    }

    @DeleteMapping("/sections/{id}")
    public ApiResponse<Void> deleteSection(@PathVariable UUID id) {
        service.deleteSection(id);
        return ApiResponse.ok(null);
    }
}
