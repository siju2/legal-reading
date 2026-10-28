package com.example.project.legal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.http.*;
import jakarta.servlet.http.HttpServletResponse;
@ControllerAdvice
public class LegalErrors {
 @ExceptionHandler(ResponseStatusException.class) String status(ResponseStatusException e,Model m,HttpServletResponse response){response.setStatus(e.getStatusCode().value());m.addAttribute("code",e.getStatusCode().value());m.addAttribute("message",e.getReason()==null?"요청한 자료를 찾을 수 없습니다.":e.getReason());return "legal/error";}
 @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class}) String conflict(Exception e,Model m,HttpServletResponse response){response.setStatus(409);m.addAttribute("code",409);m.addAttribute("message","다른 요청이 먼저 저장되었습니다. 페이지를 새로 열고 다시 확인해 주세요.");return "legal/error";}
}
