package mr.btp.api.common.i18n;

public record MessageKey(String code, String defaultMessage, Object... args) {

    public static MessageKey of(String code, String defaultMessage, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args.clone();
        return new MessageKey(code, defaultMessage, safeArgs);
    }
}
