/**
 * SharedData.java, 16.05.2008
 */
package qbf.egorov.verifier.concurrent;

import qbf.egorov.util.concurrent.DfsStackTreeNode;
import qbf.egorov.verifier.ISharedData;
import qbf.egorov.verifier.automata.IIntersectionTransition;
import qbf.egorov.verifier.automata.IntersectionNode;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared data for all simultaneous threads
 *
 * @author Kirill Egorov
 */
public class SharedData implements ISharedData {
    /**
     * Thread checked not emptytness of automatas intersection,
     * write his cotrary instance stack and other threads can terminate their execution.
     */
    private AtomicReference<DfsStackTreeNode<IIntersectionTransition>> contraryInstance =
            new AtomicReference<DfsStackTreeNode<IIntersectionTransition>>();

    /**
     * Visited nodes. Sould be concurrent or synchronized set when is used sumultaneusly.
     */
    private final Set<IntersectionNode> visited;

    public SharedData(Set<IntersectionNode> visited) {
        if (visited == null) {
            throw new IllegalArgumentException();
        }
        this.visited = visited;
    }

    public DfsStackTreeNode<IIntersectionTransition> getContraryInstance() {
        return contraryInstance.get();
    }

    public boolean setContraryInstance(DfsStackTreeNode<IIntersectionTransition> contraryInstance) {
        return this.contraryInstance.compareAndSet(null, contraryInstance);
    }

    public Set<IntersectionNode> getVisited() {
        return visited;
    }
}
