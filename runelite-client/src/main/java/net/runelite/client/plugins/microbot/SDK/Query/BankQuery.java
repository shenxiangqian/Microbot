package net.runelite.client.plugins.microbot.SDK.Query;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.Optional;
import java.util.stream.Stream;

public class BankQuery extends ItemQuery<Rs2ItemModel,BankQuery> {
    public BankQuery() {
        super();
    }

    public Optional<Rs2ItemModel> findFirst(){
        return Rs2Bank.getAll(getPredicate()).findFirst();
    }

    public Stream<Rs2ItemModel> findAll(){return Rs2Bank.getAll(getPredicate());}

    public boolean isAny(){return findFirst().isPresent();}

    public int count(){return findAll().mapToInt(Rs2ItemModel::getQuantity).sum();}

    @Override
    protected BankQuery getSelf() {
        return this;
    }
}
