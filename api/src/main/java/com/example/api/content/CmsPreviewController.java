package com.example.api.content;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/content/preview")
public class CmsPreviewController {

    private final CmsPreviewTokenService tokenService;
    private final CmsPreviewContentService contentService;

    public CmsPreviewController(
            CmsPreviewTokenService tokenService,
            CmsPreviewContentService contentService
    ) {
        this.tokenService = tokenService;
        this.contentService = contentService;
    }

    @GetMapping("/session")
    public ResponseEntity<CmsPreviewContentService.PreviewTarget> session(
            @RequestHeader(value = "X-CMS-Preview-Token", required = false) String token
    ) {
        CmsPreviewTokenService.PreviewClaims claims = tokenService.verify(token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(contentService.load(claims));
    }
}
