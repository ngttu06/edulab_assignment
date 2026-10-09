package vn.edu.khtn8.ai;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.khtn8.identity.security.AuthenticatedUser;

@RestController
public class AiModelController {
    private final AiModelService models;
    public AiModelController(AiModelService models) {this.models=models;}
    @PostMapping("/api/v1/admin/ai-settings/models")
    public ResponseEntity<AiModelService.Models> models(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody AiSettingsService.ModelsInput input) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(models.fetch(actor,input));
    }
}