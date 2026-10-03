package dev.jiahao.sourcedesk;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error",e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> invalidRequest(Exception e) { return ResponseEntity.badRequest().body(Map.of("error","Enter a question up to 1,500 characters and select your documents.")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> tooLarge(Exception e) { return ResponseEntity.status(413).body(Map.of("error","File is too large. Maximum upload size is 10 MB.")); }
}
