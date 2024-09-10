package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import searchengine.dto.statistics.ErrorResponse;

@RestControllerAdvice
public class ExceptionHandlerController {

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse illegalStateException(IllegalStateException e) {
        return new ErrorResponse(false, e.getMessage());
    }
}
