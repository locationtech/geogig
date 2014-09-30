package org.locationtech.geogig.api.plumbing.diff;

import java.util.Stack;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
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

            static Entry tree(Node left, Node right, boolean accepted) {
                Entry e = new Entry();
                e.left = left;
                e.right = right;
                e.accepted = accepted;
                return e;
            }

            static Entry bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right,
                    boolean accepted) {
                Entry e = new Entry();
                e.left = left;
                e.right = right;
                e.bucketIndex = bucketIndex;
                e.bucketDepth = bucketDepth;
                e.accepted = accepted;
                return e;
            }

            public void apply(Consumer consumer) {
                if (accepted) {
                    if (isNode()) {
                        consumer.tree((Node) left, (Node) right);
                    } else {
                        consumer.bucket(bucketIndex, bucketDepth, (Bucket) left, (Bucket) right);
                    }
                }
            }

            private boolean isNode() {
                return left == null ? right instanceof Node : left instanceof Node;
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
        public void feature(Node left, Node right) {
            consumer.feature(left, right);
        }

        @Override
        public boolean tree(Node left, Node right) {
            boolean accept = filter.apply(left) || filter.apply(right);
            stack.push(Entry.tree(left, right, accept));
            return accept;
        }

        @Override
        public void endTree(Node left, Node right) {
            Entry entry = stack.pop();
            entry.apply(consumer);
        }

        @Override
        public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            boolean accept = filter.apply(left) || filter.apply(right);
            stack.push(Entry.bucket(bucketIndex, bucketDepth, left, right, accept));
            return accept;
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            Entry entry = stack.pop();
            entry.apply(consumer);
        }
    }

    public static interface Consumer {

        public abstract void feature(@Nullable final Node left, @Nullable final Node right);

        public abstract void tree(@Nullable final Node left, @Nullable final Node right);

        public abstract void bucket(final int bucketIndex, final int bucketDepth,
                @Nullable final Bucket left, @Nullable final Bucket right);

    }
}
