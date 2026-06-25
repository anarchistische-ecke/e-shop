package com.example.api.catalog.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class MediaDerivativeConfig {
    private final List<Integer> widths;
    private final Map<String, Format> formats;
    private final String cacheControl;

    public MediaDerivativeConfig(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("media-derivatives.json").getInputStream()) {
            Config config = objectMapper.readValue(input, Config.class);
            this.widths = List.copyOf(config.widths());
            this.formats = Map.copyOf(config.formats());
            this.cacheControl = config.cacheControl();
        } catch (IOException error) {
            throw new IllegalStateException("Could not load media derivative configuration", error);
        }
    }

    public List<Integer> widths() {
        return widths;
    }

    public Map<String, Format> formats() {
        return formats;
    }

    public String cacheControl() {
        return cacheControl;
    }

    public record Format(int quality, String contentType) {
    }

    private record Config(List<Integer> widths, Map<String, Format> formats, String cacheControl) {
    }
}
