/**
 * ParseException.java, 05.03.2008
 */
package qbf.egorov.statemachine.impl;

/**
 * TODO: add comment
 *
 * @author Kirill Egorov
 */
public class ParseException extends RuntimeException {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(Throwable cause) {
        super(cause);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
