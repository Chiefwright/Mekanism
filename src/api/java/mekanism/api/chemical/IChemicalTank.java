package mekanism.api.chemical;

import javax.annotation.ParametersAreNonnullByDefault;
import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.Action;
import mekanism.api.NBTConstants;
import mekanism.api.inventory.AutomationType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface IChemicalTank<CHEMICAL extends Chemical<CHEMICAL>, STACK extends ChemicalStack<CHEMICAL>> extends INBTSerializable<CompoundNBT> {

    /**
     * Helper to get the empty stack for the {@link Chemical} type that this {@link IChemicalTank} stores.
     *
     * @return The empty stack instance.
     */
    STACK getEmptyStack();

    /**
     * Helper for creating a stack of the type this {@link IChemicalTank} is storing.
     *
     * @param stored The stack to copy the type of.
     * @param size   The size of the new stack.
     *
     * @return A new stack
     */
    STACK createStack(STACK stored, int size);

    /**
     * Returns the {@link ChemicalStack} in this tank.
     *
     * <p>
     * <strong>IMPORTANT:</strong> This {@link ChemicalStack} <em>MUST NOT</em> be modified. This method is not for
     * altering internal contents. Any implementers who are able to detect modification via this method should throw an exception. It is ENTIRELY reasonable and likely
     * that the stack returned here will be a copy.
     * </p>
     *
     * <p>
     * <strong><em>SERIOUSLY: DO NOT MODIFY THE RETURNED CHEMICAL STACK</em></strong>
     * </p>
     *
     * @return {@link ChemicalStack} in a given tank. EMPTY instance of the {@link ChemicalStack} if the tank is empty.
     */
    STACK getStack();

    /**
     * Overrides the stack in this {@link IChemicalTank}.
     *
     * @param stack {@link ChemicalStack} to set this tanks' contents to (may be empty).
     *
     * @throws RuntimeException if this tank is called in a way that it was not expecting.
     * @implNote If the internal stack does get updated make sure to call {@link #onContentsChanged()}
     */
    void setStack(STACK stack);

    /**
     * <p>
     * Inserts a {@link ChemicalStack} into this {@link IChemicalTank} and return the remainder. The {@link ChemicalStack} <em>should not</em> be modified in this
     * function!
     * </p>
     * Note: This behaviour is subtly different from {@link IFluidHandler#fill(FluidStack, FluidAction)}
     *
     * @param stack          {@link ChemicalStack} to insert. This must not be modified by the tank.
     * @param action         The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     * @param automationType The method that this tank is being interacted from.
     *
     * @return The remaining {@link ChemicalStack} that was not inserted (if the entire stack is accepted, then return an empty {@link ChemicalStack}). May be the same as
     * the input {@link ChemicalStack} if unchanged, otherwise a new {@link ChemicalStack}. The returned {@link ChemicalStack} can be safely modified after
     *
     * @implNote The {@link ChemicalStack} <em>should not</em> be modified in this function! If the internal stack does get updated make sure to call {@link
     * #onContentsChanged()}. It is also recommended to override this if your internal {@link ChemicalStack} is mutable so that a copy does not have to be made every run
     */
    default STACK insert(STACK stack, Action action, AutomationType automationType) {
        if (stack.isEmpty() || !isValid(stack)) {
            //"Fail quick" if the given stack is empty or we can never insert the item or currently are unable to insert it
            return stack;
        }
        int needed = getNeeded();
        if (needed <= 0) {
            //Fail if we are a full tank
            return stack;
        }
        boolean sameType = false;
        if (isEmpty() || (sameType = isTypeEqual(stack))) {
            int toAdd = Math.min(stack.getAmount(), needed);
            if (action.execute()) {
                //If we want to actually insert the item, then update the current item
                if (sameType) {
                    //We can just grow our stack by the amount we want to increase it
                    // Note: this also will mark that the contents changed
                    growStack(toAdd, action);
                } else {
                    //If we are not the same type then we have to copy the stack and set it
                    // Just set it unchecked as we have already validated it
                    // Note: this also will mark that the contents changed
                    setStack(createStack(stack, toAdd));
                }
            }
            return createStack(stack, stack.getAmount() - toAdd);
        }
        //If we didn't accept this chemical, then just return the given stack
        return stack;
    }

    /**
     * Extracts a {@link ChemicalStack} from this {@link IChemicalTank}.
     * <p>
     * The returned value must be empty if nothing is extracted, otherwise its stack size must be less than or equal to {@code amount}.
     * </p>
     *
     * @param amount         Amount to extract (may be greater than the current stack's max limit)
     * @param action         The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     * @param automationType The method that this tank is being interacted from.
     *
     * @return {@link ChemicalStack} extracted from the tank, must be empty if nothing can be extracted. The returned {@link ChemicalStack} can be safely modified after,
     * so the tank should return a new or copied stack.
     *
     * @implNote The returned {@link ChemicalStack} can be safely modified after, so a new or copied stack should be returned. If the internal stack does get updated make
     * sure to call {@link #onContentsChanged()}. It is also recommended to override this if your internal {@link ChemicalStack} is mutable so that a copy does not have
     * to be made every run
     */
    default STACK extract(int amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount < 1) {
            return getEmptyStack();
        }
        STACK ret = createStack(getStack(), Math.min(getStored(), amount));
        if (!ret.isEmpty() && action.execute()) {
            // Note: this also will mark that the contents changed
            shrinkStack(ret.getAmount(), action);
        }
        return ret;
    }

    /**
     * Retrieves the maximum stack size allowed to exist in this {@link IChemicalTank}.
     *
     * @return The maximum stack size allowed in this {@link IChemicalTank}.
     */
    int getCapacity();

    /**
     * <p>
     * This function should be used instead of simulated insertions in cases where the contents and state of the tank are irrelevant, mainly for the purpose of automation
     * and logic.
     * </p>
     * <ul>
     * <li>isValid is false when insertion of the chemical is never valid.</li>
     * <li>When isValid is true, no assumptions can be made and insertion must be simulated case-by-case.</li>
     * <li>The actual chemical stacks in the tank, its fullness, or any other state are <strong>not</strong> considered by isValid.</li>
     * </ul>
     *
     * @param stack Stack to test with for validity
     *
     * @return true if this {@link IChemicalTank} can accept the {@link ChemicalStack}, not considering the current state of the tank. false if this {@link IChemicalTank}
     * can never insert the {@link ChemicalStack} in any situation.
     */
    boolean isValid(STACK stack);

    /**
     * Called when the contents of this tank changes.
     */
    void onContentsChanged();

    /**
     * Convenience method for modifying the size of the stored stack.
     *
     * If there is a stack stored in this tank, set the size of it to the given amount. Capping at this chemical tank's limit. If the amount is less than or equal to
     * zero, then this instead sets the stack to the empty stack.
     *
     * @param amount The desired size to set the stack to.
     * @param action The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return Actual size the stack was set to.
     *
     * @implNote It is recommended to override this if your internal {@link ChemicalStack} is mutable so that a copy does not have to be made every run. If the internal
     * stack does get updated make sure to call {@link #onContentsChanged()}
     */
    default int setStackSize(int amount, Action action) {
        if (isEmpty()) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setEmpty();
            }
            return 0;
        }
        int maxStackSize = getCapacity();
        if (amount > maxStackSize) {
            amount = maxStackSize;
        }
        if (getStored() == amount || action.simulate()) {
            //If our size is not changing or we are only simulating the change, don't do anything
            return amount;
        }
        setStack(createStack(getStack(), amount));
        return amount;
    }

    /**
     * Convenience method for growing the size of the stored stack.
     *
     * If there is a stack stored in this tank, increase its size by the given amount. Capping at this chemical tank's limit. If the stack shrinks to an amount of less
     * than or equal to zero, then this instead sets the stack to the empty stack.
     *
     * @param amount The desired size to set the stack to.
     * @param action The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return Actual amount the stack grew.
     *
     * @apiNote Negative values for amount are valid, and will instead cause the stack to shrink.
     * @implNote If the internal stack does get updated make sure to call {@link #onContentsChanged()}
     */
    default int growStack(int amount, Action action) {
        int current = getStored();
        int newSize = setStackSize(current + amount, action);
        return newSize - current;
    }

    /**
     * Convenience method for shrinking the size of the stored stack.
     *
     * If there is a stack stored in this tank, shrink its size by the given amount. If this causes its size to become less than or equal to zero, then the stack is set
     * to the empty stack. If this method is used to grow the stack the size gets capped at this chemical tank's limit.
     *
     * @param amount The desired size to set the stack to.
     * @param action The action to perform, either {@link Action#EXECUTE} or {@link Action#SIMULATE}
     *
     * @return Actual amount the stack grew.
     *
     * @apiNote Negative values for amount are valid, and will instead cause the stack to grow.
     * @implNote If the internal stack does get updated make sure to call {@link #onContentsChanged()}
     */
    default int shrinkStack(int amount, Action action) {
        return -growStack(-amount, action);
    }

    /**
     * Convenience method for checking if this tank is empty.
     *
     * @return True if the tank is empty, false otherwise.
     *
     * @implNote If your implementation of {@link #getStack()} returns a copy, this should be overridden to directly check against the internal stack.
     */
    default boolean isEmpty() {
        return getStack().isEmpty();
    }

    /**
     * Convenience method for emptying this {@link IChemicalTank}.
     */
    default void setEmpty() {
        setStack(getEmptyStack());
    }

    /**
     * Convenience method for checking the amount of chemical in this tank.
     *
     * @return The size of the stored stack, or zero is the stack is empty.
     *
     * @implNote If your implementation of {@link #getStack()} returns a copy, this should be overridden to directly check against the internal stack.
     */
    default int getStored() {
        return getStack().getAmount();
    }

    /**
     * Gets the amount of chemical needed by this {@link IChemicalTank} to reach a filled state.
     *
     * @return Amount of chemical needed
     */
    default int getNeeded() {
        return Math.max(0, getCapacity() - getStored());
    }

    /**
     * Convenience method for getting the type of the {@link Chemical} stored in this tank.
     *
     * @return chemical type contained
     */
    default CHEMICAL getType() {
        return getStack().getType();
    }

    /**
     * Convenience method for checking if this tank's contents are of an equal type to a given chemical stack's.
     *
     * @param other The stack to compare to.
     *
     * @return True if the tank's contents are equal, false otherwise.
     *
     * @implNote If your implementation of {@link #getStack()} returns a copy, this should be overridden to directly check against the internal stack.
     */
    default boolean isTypeEqual(STACK other) {
        return getStack().isTypeEqual(other);
    }

    /**
     * Convenience method for checking if this tank's contents are of an equal type to a given chemical's.
     *
     * @param other The chemical to compare to.
     *
     * @return True if the tank's contents are equal, false otherwise.
     *
     * @implNote If your implementation of {@link #getStack()} returns a copy, this should be overridden to directly check against the internal stack.
     */
    default boolean isTypeEqual(CHEMICAL other) {
        return getStack().isTypeEqual(other);
    }

    @Override
    default CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        if (!isEmpty()) {
            nbt.put(NBTConstants.STORED, getStack().write(new CompoundNBT()));
        }
        return nbt;
    }
}