package vn.career.common.api;

import java.util.List;

public record ErrorBody(String code, String message, List<ErrorDetail> details) {
}
