package OnePlayerSleep.API.types;

/**
 * Message
 *
 * a single sleep message
 */
public interface Message {
    /**
     * @return name of message
     */
    String getName();

    /**
     * @return raw message contents
     */
    String getMessage();

    /**
     * @return raw message hover contents
     */
    String getHover();

    /**
     * @return raw message wakeup contents
     */
    String getWakeup();

    /**
     * @return raw message wakeup failure response contents
     */
    String getCantWakeup();

    /**
     * @return message chance within its current group
     */
    Double getChance();
}
