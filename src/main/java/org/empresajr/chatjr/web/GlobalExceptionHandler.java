package org.empresajr.chatjr.web;

import jakarta.servlet.http.HttpServletRequest;
import org.empresajr.chatjr.service.AppErrorService;
import org.empresajr.chatjr.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Converte qualquer erro em JSON com mensagem em português. Nunca devolve detalhes técnicos ao usuário. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AppErrorService appErrors;

    public GlobalExceptionHandler(AppErrorService appErrors) {
        this.appErrors = appErrors;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst().orElse("Os dados enviados são inválidos.");
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("A requisição está incompleta ou mal formada."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("O arquivo é maior que o limite permitido."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException e) {
        log.warn("Conflito de dados: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("Já existe um registro com esses dados."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("Você não tem permissão para esta ação."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception e, HttpServletRequest request) {
        // Erros do próprio Spring MVC (rota inexistente, método errado...) já trazem o status correto.
        if (e instanceof org.springframework.web.ErrorResponse mvc) {
            HttpStatusCode status = mvc.getStatusCode();
            return ResponseEntity.status(status).body(new ApiError(messageFor(status)));
        }
        log.error("Erro inesperado", e);
        appErrors.record(e.getClass().getSimpleName() + ": " + e.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                "Ocorreu um erro inesperado. Tente de novo; se continuar, avise a consultoria."));
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Recurso não encontrado.";
            case 405 -> "Operação não permitida para este endereço.";
            case 415 -> "Formato de envio não aceito.";
            case 400 -> "A requisição está incompleta ou mal formada.";
            default -> "Não foi possível concluir a operação.";
        };
    }
}
