package mr.btp.api.common.exception;

import java.text.MessageFormat;
import java.util.Arrays;
import mr.btp.api.common.i18n.MessageKey;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String messageCode;
    private final Object[] messageArgs;

    public ApiException(HttpStatus status, String message) {
        this(status, null, message);
    }

    public ApiException(HttpStatus status, String messageCode, String message, Object... messageArgs) {
        super(formatMessage(message, messageArgs));
        this.status = status;
        this.messageCode = messageCode;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getMessageArgs() {
        return messageArgs.clone();
    }

    private static String formatMessage(String template, Object[] args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        Object[] resolvedArgs = Arrays.stream(args)
                .map(ApiException::formatArgument)
                .toArray();
        return MessageFormat.format(template, resolvedArgs);
    }

    private static Object formatArgument(Object arg) {
        if (arg instanceof MessageKey messageKey) {
            return formatMessage(messageKey.defaultMessage(), messageKey.args());
        }
        if (arg instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return arg;
    }
}
