package com.example.api.content;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Component
public class CmsHtmlSanitizer {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "mailto", "tel");
    private static final Safelist CONTENT_SAFELIST = Safelist.none()
            .addTags(
                    "p", "br", "strong", "em", "b", "i", "u", "s",
                    "ul", "ol", "li", "blockquote", "h2", "h3", "h4",
                    "table", "thead", "tbody", "tr", "th", "td", "a"
            )
            .addAttributes("a", "href", "title")
            .addAttributes("th", "scope")
            .preserveRelativeLinks(true);

    public String sanitize(String html) {
        if (!StringUtils.hasText(html)) {
            return html;
        }

        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(html, "", CONTENT_SAFELIST, outputSettings);
        Document fragment = Jsoup.parseBodyFragment(cleaned);
        fragment.outputSettings(outputSettings);
        for (Element link : fragment.select("a[href]")) {
            String href = link.attr("href").trim();
            if (!isSafeLink(href)) {
                link.removeAttr("href");
                continue;
            }
            if (href.startsWith("https://")) {
                link.attr("rel", "noopener noreferrer");
            }
        }
        return fragment.body().html();
    }

    public boolean isSafeLink(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String link = value.trim();
        if (
                (link.startsWith("/") && !link.startsWith("//"))
                || (link.startsWith("#") && link.length() > 1)
        ) {
            return !containsControlCharacters(link);
        }
        try {
            URI uri = URI.create(link);
            String scheme = uri.getScheme();
            return scheme != null
                    && ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                    && !containsControlCharacters(link);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private boolean containsControlCharacters(String value) {
        return value.chars().anyMatch(character -> Character.isISOControl(character));
    }
}
