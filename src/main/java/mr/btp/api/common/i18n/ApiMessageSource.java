package mr.btp.api.common.i18n;

import java.util.Locale;
import mr.btp.api.common.exception.ApiException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;

@Component
public class ApiMessageSource {

    private final MessageSource messageSource;

    public ApiMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getMessage(String code, String defaultMessage, Object... args) {
        return getMessage(code, defaultMessage, LocaleContextHolder.getLocale(), args);
    }

    public String getMessage(String code, String defaultMessage, Locale locale, Object... args) {
        if (code != null && !code.isBlank()) {
            return messageSource.getMessage(code, resolveArguments(args, locale), defaultMessage, locale);
        }
        return defaultMessage;
    }

    public String getMessage(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, resolveArguments(args, locale), code, locale);
    }

    public String getMessage(String code, Object... args) {
        return getMessage(code, LocaleContextHolder.getLocale(), args);
    }

    public String getMessage(DefaultMessageSourceResolvable resolvable, String defaultMessage) {
        return messageSource.getMessage(resolvable, LocaleContextHolder.getLocale());
    }

    public String resolve(ApiException exception) {
        String code = exception.getMessageCode();
        String defaultMessage = exception.getMessage();
        if (code == null || code.isBlank()) {
            code = defaultMessage;
        }
        return getMessage(code, defaultMessage, exception.getMessageArgs());
    }

    private Object[] resolveArguments(Object[] args, Locale locale) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        Object[] resolved = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            resolved[index] = resolveArgument(args[index], locale);
        }
        return resolved;
    }

    private Object resolveArgument(Object arg, Locale locale) {
        if (arg instanceof MessageKey messageKey) {
            return getMessage(messageKey.code(), messageKey.defaultMessage(), locale, messageKey.args());
        }
        if (arg instanceof Enum<?> enumValue) {
            return getMessage(enumMessageCode(enumValue), enumValue.name(), locale);
        }
        return arg;
    }

    private String enumMessageCode(Enum<?> enumValue) {
        String typeName = enumValue.getDeclaringClass().getSimpleName()
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
        return "enum." + typeName + "." + enumValue.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
