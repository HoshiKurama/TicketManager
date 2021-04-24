package engineer.hoshikurama.github.ticketmanager.v2.databases;

import engineer.hoshikurama.github.ticketmanager.v2.DatabaseException;
import engineer.hoshikurama.github.ticketmanager.v2.TMInvalidDataException;
import engineer.hoshikurama.github.ticketmanager.v2.Ticket;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public interface Database {
    Ticket getTicket(int ID) throws DatabaseException, TMInvalidDataException;

    void createTicket(Ticket ticket) throws DatabaseException;

    void updateTicket(Ticket ticket) throws DatabaseException;

    int getNextTicketID() throws DatabaseException;

    List<Ticket> getOpenTickets() throws DatabaseException;

    List<Ticket> getTicketsWithUUID(UUID uuid) throws DatabaseException;

    List<Ticket> getUnreadTickets() throws DatabaseException;

    void createTableIfMissing() throws DatabaseException;

    void massCloseTickets(int lowerID, int upperID) throws DatabaseException;

    List<Ticket> dbSearchCommand(CommandSender sender, List<String> searches, AtomicInteger atomicPage) throws DatabaseException;

    boolean conversionIsRequired();

    void initiateConversionProcess();

    List<Ticket> getUnreadTicketsForPlayer(Player player) throws DatabaseException;
}
