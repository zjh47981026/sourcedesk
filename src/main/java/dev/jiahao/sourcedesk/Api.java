package dev.jiahao.sourcedesk;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class Api {
    public record Question(@NotBlank @Size(max=1500) String question,@NotEmpty @Size(max=20) List<String> documentIds) {}
    private final Documents documents; private final Retrieval retrieval; private final Answers answers;
    public Api(Documents documents,Retrieval retrieval,Answers answers) { this.documents=documents; this.retrieval=retrieval; this.answers=answers; }
    @GetMapping("/status") public Map<String,Object> status() { return Map.of("modelConfigured",answers.configured(),"storage","session","app","SourceDesk"); }
    @GetMapping("/documents") public List<Documents.Summary> list(HttpSession s) { return documents.list(s); }
    @PostMapping("/documents") public Documents.Summary upload(@RequestParam("file") MultipartFile file,HttpSession s) throws IOException { return documents.upload(s,file); }
    @PostMapping("/demo") public List<Documents.Summary> demo(HttpSession s) { documents.demo(s); return documents.list(s); }
    @DeleteMapping("/documents/{id}") public Map<String,String> delete(@PathVariable String id,HttpSession s) { documents.delete(s,id); return Map.of("status","removed"); }
    @DeleteMapping("/documents") public Map<String,String> clear(HttpSession s) { documents.clear(s); return Map.of("status","cleared"); }
    @PostMapping("/ask") public Answers.Result ask(@Valid @RequestBody Question q,HttpSession s) {
        var result=answers.answer(q.question().trim(),retrieval.search(q.question(),documents.selected(s,q.documentIds())));
        documents.selected(s,q.documentIds()); // Reject answers if a source was removed during generation.
        return result;
    }
}
