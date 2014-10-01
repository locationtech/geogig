package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.hooks.Hookable;
import org.locationtech.geogig.api.porcelain.TransferSummary;

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
