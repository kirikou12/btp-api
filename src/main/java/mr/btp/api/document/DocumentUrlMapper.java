package mr.btp.api.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DocumentUrlMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private DocumentUrlMapper() {
    }

    public static List<String> normalize(List<String> documentUrls, String legacyDocumentRef) {
        if (documentUrls != null) {
            return clean(documentUrls);
        }
        return parseLegacyDocumentRef(legacyDocumentRef);
    }

    public static String toLegacyDocumentRef(List<String> documentUrls) {
        List<String> urls = clean(documentUrls);
        if (urls.isEmpty()) {
            return null;
        }
        if (urls.size() == 1) {
            return urls.getFirst();
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(urls);
        } catch (JsonProcessingException exception) {
            return String.join(",", urls);
        }
    }

    private static List<String> parseLegacyDocumentRef(String legacyDocumentRef) {
        if (legacyDocumentRef == null || legacyDocumentRef.isBlank()) {
            return List.of();
        }

        String value = legacyDocumentRef.trim();
        if (value.startsWith("[")) {
            try {
                return clean(OBJECT_MAPPER.readValue(value, STRING_LIST));
            } catch (JsonProcessingException ignored) {
                return clean(List.of(value));
            }
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            try {
                return clean(List.of(OBJECT_MAPPER.readValue(value, String.class)));
            } catch (JsonProcessingException ignored) {
                return clean(List.of(value));
            }
        }
        return clean(List.of(value));
    }

    private static List<String> clean(List<String> documentUrls) {
        Set<String> urls = new LinkedHashSet<>();
        for (String documentUrl : documentUrls == null ? List.<String>of() : documentUrls) {
            if (documentUrl != null && !documentUrl.isBlank()) {
                urls.add(documentUrl.trim());
            }
        }
        return new ArrayList<>(urls);
    }
}
