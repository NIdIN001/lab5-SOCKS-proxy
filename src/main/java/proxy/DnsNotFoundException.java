package proxy;

public class DnsNotFoundException extends Exception {
    public DnsNotFoundException() {
        super();
    }

    public DnsNotFoundException(String message) {
        super(message);
    }

    public DnsNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public DnsNotFoundException(Throwable cause) {
        super(cause);
    }

    protected DnsNotFoundException(String message, Throwable cause,
                                   boolean enableSuppression,
                                   boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
