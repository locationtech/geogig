package org.locationtech.geogig.api.plumbing.diff;

import java.util.Stack;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * Provides a means to "walk" the differences between two {@link RevTree trees} in depth-first order
 * and emit diff events to a {@link Consumer}.
 * <p>
 * Skipping whole subtrees can be achieved by passing a {@link Predicate Predicate<Bounded>} that
 * will be evaluated for each pair of tree {@link Node nodes} or {@link Bucket buckets}.
 */
public class PostOrderDiffWalk {
    private static final Predicate<Bounded> ACEPT_ALL = Predicates.alwaysTrue();

    private PreOrderDiffWalk inOrder;

    public PostOrderDiffWalk(RevTree left, RevTree right, ObjectDatabase leftSource,
            ObjectDatabase rightSource) {
        this.inOrder = new PreOrderDiffWalk(left, right, leftSource, rightSource);
    }

    public final void walk(final Consumer consumer) {
        walk(ACEPT_ALL, consumer);
    }

    public final void walk(final Predicate<Bounded> filter, final Consumer consumer) {
        DepthFirstConsumer depthFirstConsumer = new DepthFirstConsumer(filter, consumer);
        inOrder.walk(depthFirstConsumer);
    }

    private static class DepthFirstConsumer implements
            org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.Consumer {

        private static final class Entry {
            private Bounded left;

            private Bounded right;

            private int bucketIndex;

            private int bucketDepth;

            private boolean accepted;

            private NodeRef leftParent;

            private NodeRef rightParent;

            static Entry tree(NodeRef left, NodeRef right, boolean accepted) {
                Entry e = new Entry();
                e.left = left;
                e.right = right;
                e.accepted = accepted;
                return e;
            }

            static Entry bucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                    int bucketDepth, Bucket left, Bucket right, boolean accepted) {
                Entry e = new Entry();
                e.left = left;
                e.right = right;
                e.leftParent = leftParent;
                e.rightParent = rightParent;
                e.bucketIndex = bucketIndex;
                e.bucketDepth = bucketDepth;
                e.accepted = accepted;
                return e;
            }

            public void apply(Consumer consumer) {
                if (accepted) {
                    if (isNode()) {
                        consumer.tree((NodeRef) left, (NodeRef) right);
                    } else {
                        Bucket lbucket = (Bucket) left;
                        Bucket rbucket = (Bucket) right;
                        consumer.bucket(leftParent, rightParent, bucketIndex, bucketDepth, lbucket,
                                rbucket);
                    }
                }
            }

            private boolean isNode() {
                return left == null ? right instanceof NodeRef : left instanceof NodeRef;
            }
        }

        private Stack<Entry> stack = new Stack<>();

        private Predicate<Bounded> filter;

        private Consumer consumer;

        public DepthFirstConsumer(Predicate<Bounded> filter, Consumer consumer) {
            this.filter = filter;
            this.consumer = consumer;
        }

        @Override
        public boolean feature(NodeRef left, NodeRef right) {
            boolean accept = filter.apply(left) || filter.apply(right);
            if (accept) {
                consumer.feature(left, right);
            }
            return true;
        }

        @Override
        public boolean tree(NodeRef left, NodeRef right) {
            boolean accept = filter.apply(left) || filter.apply(right);
            stack.push(Entry.tree(left, right, accept));
            return accept;
        }

        @Override
        public void endTree(NodeRef left, NodeRef right) {
            Entry entry = stack.pop();
            entry.apply(consumer);
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            boolean accept = filter.apply(left) || filter.apply(right);
            stack.push(Entry.bucket(leftParent, rightParent, bucketIndex, bucketDepth, left, right,
                    accept));
            return accept;
        }

        @Override
        public void endBucket(NodeRef leftParent, NodeRef rightParent, int bucketIndex,
                int bucketDepth, Bucket left, Bucket right) {
            Entry entry = stack.pop();
            entry.apply(consumer);
        }
    }

    public static interface Consumer {

        public abstract void feature(@Nullable final NodeRef left, @Nullable final NodeRef right);

        public abstract void tree(@Nullable final NodeRef left, @Nullable final NodeRef right);

        public abstract void bucket(@Nullable final NodeRef leftParent,
                @Nullable final NodeRef rightParent, final int bucketIndex, final int bucketDepth,
                @Nullable final Bucket left, @Nullable final Bucket right);

    }
}
