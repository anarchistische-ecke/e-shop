package com.example.api.content;

import com.example.api.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CmsPreviewControllerTest {

    @Mock
    private CmsPreviewTokenService tokenService;

    @Mock
    private CmsPreviewContentService contentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CmsPreviewController(tokenService, contentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void session_withoutToken_returnsUnauthorizedPreviewError() throws Exception {
        when(tokenService.verify(null))
                .thenThrow(new CmsPreviewUnauthorizedException("Preview token is required"));

        mockMvc.perform(get("/content/preview/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMS_PREVIEW_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Preview token is required"));

        verify(tokenService).verify(null);
    }

    @Test
    void session_withMalformedToken_returnsUnauthorizedPreviewError() throws Exception {
        when(tokenService.verify("invalid"))
                .thenThrow(new CmsPreviewUnauthorizedException("Malformed preview token"));

        mockMvc.perform(get("/content/preview/session")
                        .header("X-CMS-Preview-Token", "invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMS_PREVIEW_UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Malformed preview token"));

        verify(tokenService).verify("invalid");
    }
}
