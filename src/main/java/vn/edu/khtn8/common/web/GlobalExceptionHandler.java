package vn.edu.khtn8.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    Object validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).collect(Collectors.joining("; "));
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    Object status(ResponseStatusException ex, HttpServletRequest request) {
        return error(request, ex.getStatusCode(), "REQUEST_FAILED", ex.getReason());
    }

    @ExceptionHandler(AuthenticationException.class)
    Object authentication(HttpServletRequest request) {
        return error(request, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Tên đăng nhập hoặc mật khẩu không đúng.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    Object denied(HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    Object malformed(HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu gửi lên không hợp lệ.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    Object conflict(HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "CONFLICT", "Dữ liệu đã tồn tại hoặc không còn hợp lệ.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    Object tooLarge(HttpServletRequest request) {
        return error(request, HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Tệp vượt quá dung lượng cho phép.");
    }

    @ExceptionHandler(Exception.class)
    Object unexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse response) {
            return error(request, response.getStatusCode(), "REQUEST_FAILED", "Yêu cầu không hợp lệ hoặc tài nguyên không tồn tại.");
        }
        // Do not log request bodies, SQL parameters, tokens or children's personal data.
        log.error("Request failed: {}", ex.getClass().getSimpleName());
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Đã xảy ra lỗi máy chủ.");
    }

    private Object error(HttpServletRequest request, HttpStatusCode status, String code, String message) {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            return ResponseEntity.status(status).body(new ApiError(code, message));
        }
        ModelAndView view = new ModelAndView("error");
        view.setStatus(status);
        view.addObject("message", message);
        view.addObject("status", status.value());
        return view;
    }
}
