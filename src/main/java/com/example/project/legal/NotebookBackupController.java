package com.example.project.legal;

import java.io.IOException;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class NotebookBackupController {
    private final NotebookBackupService backups;
    public NotebookBackupController(NotebookBackupService backups){this.backups=backups;}
    @GetMapping({"/notebooks/backup","/notebooks/{id}/backup"}) @ResponseBody
    ResponseEntity<byte[]> download(Principal user,@PathVariable(required=false) Long id){
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"legal-notebooks"+(id==null?"":"-"+id)+".json\"").body(backups.export(user.getName(),id));
    }
    @PostMapping("/notebooks/restore") String restore(Principal user,@RequestParam MultipartFile file,@RequestParam(defaultValue="false") boolean confirm,RedirectAttributes flash)throws IOException{
        LegalService.require(confirm,"기존 노트를 유지하고 사본으로 복원하는 데 동의해 주세요.");
        LegalService.require(!file.isEmpty()&&file.getSize()<=NotebookBackupService.MAX_BYTES,"5MB 이하의 노트 백업 JSON 파일을 선택해 주세요.");
        int count=backups.restore(user.getName(),file.getBytes());flash.addFlashAttribute("notice",count+"개 노트를 새 사본으로 복원했습니다. 기존 노트는 유지됩니다.");return "redirect:/notebooks";
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    void oversized(){throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"복원할 백업은 5MB 이하로 선택해 주세요.");}
}
