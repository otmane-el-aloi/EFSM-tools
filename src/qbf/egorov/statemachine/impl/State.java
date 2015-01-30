/**
 * State.java, 02.03.2008
 */
package qbf.egorov.statemachine.impl;

import qbf.egorov.statemachine.*;

import java.util.*;

/**
 * IState implementation. May contain nested state machine.
 *
 * @author Kirill Egorov
 */
public class State extends SimpleState {
    private Set<IStateMachine<? extends IState>> nestedStateMachines = new LinkedHashSet<IStateMachine<? extends IState>>();

    public State(String name, StateType type, List<IAction> actions) {
        super(name, type, actions);
    }

    public Set<IStateMachine<? extends IState>> getNestedStateMachines() {
        return nestedStateMachines;
    }

    public void addNestedStateMachine(IStateMachine<? extends IState> m) {
        nestedStateMachines.add(m);
    }
}
