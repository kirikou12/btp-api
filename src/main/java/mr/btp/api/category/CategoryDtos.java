package mr.btp.api.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(@NotBlank String name, @NotNull CategoryType type, Boolean isSystem) {
    }

    public record CategoryResponse(Long id, String name, String type, boolean isSystem) {
    }
}
