package kz.govflow;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
@RestController @RequestMapping("/api/requests") @CrossOrigin(origins="http://localhost:5173") public class RequestController {
 private final RequestRepository repository;
 public RequestController(RequestRepository repository){this.repository=repository;}
 @GetMapping public List<Request> list(@RequestParam(required=false) Request.Status status){return status==null?repository.findAllByOrderByCreatedAtDesc():repository.findByStatusOrderByCreatedAtDesc(status);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Request create(@Valid @RequestBody CreateRequest body){Request r=new Request();r.title=body.title();r.description=body.description();return repository.save(r);}
 @PatchMapping("/{id}/status") public Request changeStatus(@PathVariable Long id,@Valid @RequestBody ChangeStatus body){Request r=repository.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found"));r.status=body.status();return repository.save(r);}
 public record CreateRequest(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=2000) String description){}
 public record ChangeStatus(@NotNull Request.Status status){}
}