package org.locationtech.geogig.plumbing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.porcelain.TransferSummary;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

@Hookable(name = "receive-pack")
public class ReceivePack extends AbstractGeoGigOp<TransferSummary> {

    public static class Pack {

    }

    private Pack pack;

    public ReceivePack setPack(Pack pack) {
        checkNotNull(pack);
        this.pack = pack;
        return this;
    }

    public Pack getPack() {
        return pack;
    }

    @Override
    protected TransferSummary _call() {
        checkState(pack != null, "No pack supplied");
        return null;
    }

}
