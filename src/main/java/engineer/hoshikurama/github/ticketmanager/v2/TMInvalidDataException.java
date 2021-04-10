package engineer.hoshikurama.github.ticketmanager.v2;

public class TMInvalidDataException extends Exception {
    public TMInvalidDataException(String errorMessage) {
        super(errorMessage);
    }
}
