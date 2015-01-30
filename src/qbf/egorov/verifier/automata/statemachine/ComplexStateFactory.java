/**
 * ComplexStateFactory.java, 27.04.2008
 */
package qbf.egorov.verifier.automata.statemachine;

import qbf.egorov.statemachine.IState;
import qbf.egorov.statemachine.IStateMachine;
import qbf.egorov.verifier.automata.tree.ITree;
import qbf.egorov.verifier.automata.tree.ITreeNode;
import qbf.egorov.verifier.automata.tree.StateTree;
import qbf.egorov.verifier.automata.tree.TreeNode;

import java.util.Map;
import java.util.HashMap;

/**
 * TODO: add comment
 *
 * @author Kirill Egorov
 */
public class ComplexStateFactory<S extends IState> implements IComplexStateFactory<S> {

    private Map<ITree<S>, ComplexState<S>> stateMap = new HashMap<ITree<S>, ComplexState<S>>();
//    private ComplexState initial;
    
    private ComplexStateFactory() {
        //
    }

    public static <S extends IState> ComplexState createInitialState(IStateMachine<S> stateMachine) {
        ComplexStateFactory<S> factory = new ComplexStateFactory<S>();
        ITree<S> initTree = new StateTree<S>(factory.getInitial(stateMachine, true));

        return new ComplexState<S>(initTree, factory);
    }


    /*public ComplexState getInitialState(IStateMachine<IState> stateMachine) {
        if (initial == null) {
            ITree<IState> initTree = new StateTree(getInitial(stateMachine, true));
            
            initial = new ComplexState(initTree, this);
        }
        return initial;
    }*/

    public ComplexState<S> getState(ITree<S> tree) {
        ComplexState<S> res = stateMap.get(tree);
        if (res == null) {
            res = new ComplexState<S>(tree, this);
            stateMap.put(tree, res);
        }
        return res;
    }

    private ITreeNode<S> getInitial(IStateMachine<S> stateMachine, boolean active) {
        TreeNode<S> node = new TreeNode<S>(stateMachine.getInitialState(), stateMachine, active);

        for (IStateMachine<S> sm: stateMachine.getNestedStateMachines()) {
            //Only root node can be active
            node.addChildren(getInitial(sm, false));
        }
        return node;
    }
}
