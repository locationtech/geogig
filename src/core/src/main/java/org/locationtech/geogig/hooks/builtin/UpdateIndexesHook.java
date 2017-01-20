package org.locationtech.geogig.hooks.builtin;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.CannotRunGeogigOperationException;
import org.locationtech.geogig.hooks.CommandHook;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.porcelain.index.UpdateIndexesOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.ProgressListener;

import com.google.common.base.Optional;

/**
 * Hooks into {@link UpdateRef} to update all the indexes that need updating after a branch is
 * updated.
 *
 */
public class UpdateIndexesHook implements CommandHook {

    @Override
    public boolean appliesTo(Class<? extends AbstractGeoGigOp<?>> clazz) {
        return UpdateRef.class.equals(clazz);
    }

    @Override
    public <C extends AbstractGeoGigOp<?>> C pre(C command)
            throws CannotRunGeogigOperationException {
        return command;
    }

    @Override
    public <T> T post(AbstractGeoGigOp<T> command, @Nullable Object retVal,
            @Nullable RuntimeException exception) throws Exception {

        @SuppressWarnings("unchecked")
        final Optional<Ref> updatedRef = (Optional<Ref>) retVal;

        if (exception == null && updatedRef.isPresent()) {
            final Ref ref = updatedRef.get();
            final String refName = ref.getName();
            // update indexes only for branch updates
            if (refName.startsWith(Ref.REFS_PREFIX)) {
                final Context context = command.context();
                List<Index> updates;
                try {
                    ProgressListener listener = command.getProgressListener();
                    listener.started();

                    updates = context.command(UpdateIndexesOp.class)//
                            .setRef(ref)//
                            .setProgressListener(listener)//
                            .call();
                    if (!updates.isEmpty()) {
                        System.err.println("***" + updates);
                        listener.setDescription(String.format("updated indexes: %s\n", updates));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return (T) retVal;
    }
}
