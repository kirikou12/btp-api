package mr.btp.api.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank(message = "{validation.category.name.required}") String name,
            @NotNull(message = "{validation.category.type.required}") CategoryType type,
            Boolean isSystem
    ) {
    }

    public record CategoryResponse(Long id, String name, String type, boolean isSystem) {
    }
}
