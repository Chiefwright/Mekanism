package mekanism.api.transmitters;

import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.Coord4D;
import mekanism.api.math.FloatingLong;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;

public interface IGridTransmitter<ACCEPTOR, NETWORK extends DynamicNetwork<ACCEPTOR, NETWORK, BUFFER>, BUFFER> extends ITransmitter {

    boolean hasTransmitterNetwork();

    /**
     * Gets the network currently in use by this transmitter segment.
     *
     * @return network this transmitter is using
     */
    NETWORK getTransmitterNetwork();

    /**
     * Sets this transmitter segment's network to a new value.
     *
     * @param network - network to set to
     */
    void setTransmitterNetwork(NETWORK network);

    void setRequestsUpdate();

    int getTransmitterNetworkSize();

    int getTransmitterNetworkAcceptorSize();

    ITextComponent getTransmitterNetworkNeeded();

    ITextComponent getTransmitterNetworkFlow();

    ITextComponent getTransmitterNetworkBuffer();

    double getTransmitterNetworkCapacity();

    @Nonnull
    FloatingLong getCapacityAsFloatingLong();

    int getCapacity();

    World world();

    Coord4D coord();

    Coord4D getAdjacentConnectableTransmitterCoord(Direction side);

    ACCEPTOR getAcceptor(Direction side);

    boolean isValid();

    boolean isOrphan();

    void setOrphan(boolean orphaned);

    NETWORK createEmptyNetwork();

    NETWORK mergeNetworks(Collection<NETWORK> toMerge);

    NETWORK getExternalNetwork(Coord4D from);

    void takeShare();

    /**
     * @return The transmitter's buffer.
     */
    //TODO: Can we convert this to being nonnull
    @Nullable
    BUFFER getBuffer();

    /**
     * If the transmitter does not have a buffer this will try to fallback on the network's buffer.
     *
     * @return The transmitter's buffer, or if null the network's buffer.
     */
    @Nullable
    default BUFFER getBufferWithFallback() {
        BUFFER buffer = getBuffer();
        //If we don't have a buffer try falling back to the network's buffer
        if (buffer == null && hasTransmitterNetwork()) {
            return getTransmitterNetwork().getBuffer();
        }
        return buffer;
    }

    default boolean isCompatibleWith(IGridTransmitter<ACCEPTOR, NETWORK, BUFFER> other) {
        return true;
    }

    /**
     * Gets called on an orphan if at least one attempted network fails to connect due to having connected to another network that is incompatible with the next attempted
     * ones.
     *
     * This is primarily used for if extra handling needs to be done, such as refreshing the connections visually on a minor delay so that it has time to have the buffer
     * no longer be null and properly compare the connection.
     */
    default void connectionFailed() {
    }
}